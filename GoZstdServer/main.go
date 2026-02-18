package main

import (
	"flag"
	"fmt"
	"io"
	"log"
	"net"
	"os"
	"path/filepath"
	"sync"
	"sync/atomic"
	"time"

	"github.com/klauspost/compress/zstd"
	"github.com/pires/go-proxyproto"
)

var (
	totalRaw    uint64
	totalZstd   uint64
	activeConns int64

	limitMutex     sync.Mutex
	ipLimits       = make(map[string]int)
	requestHistory = make(map[string][]time.Time)
	banList        = make(map[string]time.Time)
	activeConnsMap = make(map[string]map[net.Conn]struct{})

	maxConnsPerIP  *int
	maxReqInWindow *int
	windowDuration *time.Duration
	banDuration    *time.Duration
)

func initLogger() {
	logDir := "logs"
	if _, err := os.Stat(logDir); os.IsNotExist(err) {
		os.Mkdir(logDir, 0755)
	}
	latestLog := filepath.Join(logDir, "latest.log")
	if _, err := os.Stat(latestLog); err == nil {
		timestamp := time.Now().Format("2006-01-02-15-04-05")
		os.Rename(latestLog, filepath.Join(logDir, timestamp+".log"))
	}
	logFile, _ := os.OpenFile(latestLog, os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0666)
	multi := io.MultiWriter(os.Stdout, logFile)
	log.SetOutput(multi)
	log.SetFlags(log.Ldate | log.Ltime)
}

func safeLog(format string, v ...interface{}) {
	fmt.Print("\r                                                                                        \r")
	log.Printf(format, v...)
}

func formatBytes(b uint64) string {
	const unit = 1024
	if b < unit {
		return fmt.Sprintf("%d B", b)
	}
	div, exp := uint64(unit), 0
	for n := b / unit; n >= unit; n /= unit {
		div *= unit
		exp++
	}
	return fmt.Sprintf("%.2f %cB", float64(b)/float64(div), "KMGTPE"[exp])
}

func checkAndRegister(addr net.Addr, conn net.Conn, isPPv2 bool) bool {
	limitMutex.Lock()
	defer limitMutex.Unlock()

	host, port, _ := net.SplitHostPort(addr.String())
	now := time.Now()
	connTypeStr := "Legacy"
	if isPPv2 {
		connTypeStr = "PPv2"
	}

	if unbanTime, ok := banList[host]; ok {
		if now.Before(unbanTime) {
			remaining := time.Until(unbanTime).Round(time.Second)
			safeLog("[DENIED] Banned IP %s:%s(%s) tried to connect. Remaining: %v", host, port, connTypeStr, remaining)
			return false
		}
		delete(banList, host)
	}

	var recent []time.Time
	for _, t := range requestHistory[host] {
		if now.Sub(t) < *windowDuration {
			recent = append(recent, t)
		}
	}
	recent = append(recent, now)
	requestHistory[host] = recent

	// 3. 判定是否违规
	isViolated := false
	reason := ""
	if len(recent) > *maxReqInWindow {
		isViolated = true
		reason = fmt.Sprintf("Freq too high (%d in %v)", len(recent), *windowDuration)
	} else if ipLimits[host] >= *maxConnsPerIP {
		isViolated = true
		reason = fmt.Sprintf("Too many conns (%d/%d)", ipLimits[host], *maxConnsPerIP)
	}

	if isViolated {
		safeLog("[SECURITY] Banning IP: %s(%s) | Reason: %s | Kick connections!", host, connTypeStr, reason)
		banAndKick(host)
		return false
	}

	ipLimits[host]++
	if activeConnsMap[host] == nil {
		activeConnsMap[host] = make(map[net.Conn]struct{})
	}
	activeConnsMap[host][conn] = struct{}{}

	return true
}

func banAndKick(host string) {
	banList[host] = time.Now().Add(*banDuration)
	if conns, ok := activeConnsMap[host]; ok {
		for c := range conns {
			c.Close()
		}
		delete(activeConnsMap, host)
	}
	delete(ipLimits, host)
	delete(requestHistory, host)
}

func unregister(addr net.Addr, conn net.Conn) {
	limitMutex.Lock()
	defer limitMutex.Unlock()
	host, _, _ := net.SplitHostPort(addr.String())
	if ipLimits[host] > 0 {
		ipLimits[host]--
	}
	if conns, ok := activeConnsMap[host]; ok {
		delete(conns, conn)
		if len(conns) == 0 {
			delete(activeConnsMap, host)
		}
	}
}

type statsReader struct {
	io.Reader
	counter *uint64
}

func (r *statsReader) Read(p []byte) (n int, err error) {
	n, err = r.Reader.Read(p)
	if n > 0 {
		atomic.AddUint64(r.counter, uint64(n))
	}
	return n, err
}

type statsWriter struct {
	io.Writer
	counter *uint64
}

func (w *statsWriter) Write(p []byte) (n int, err error) {
	n, err = w.Writer.Write(p)
	if n > 0 {
		atomic.AddUint64(w.counter, uint64(n))
	}
	return n, err
}

func bridge(rawConn net.Conn, zstdConn net.Conn, mode string, level int) {
	atomic.AddInt64(&activeConns, 1)
	defer func() {
		atomic.AddInt64(&activeConns, -1)
		rawConn.Close()
		zstdConn.Close()
	}()

	var networkIn io.Reader = zstdConn
	var networkOut io.Writer = zstdConn

	if mode == "server" {
		pConn := proxyproto.NewConn(zstdConn)
		header := pConn.ProxyHeader()
		if header != nil {
			if !checkAndRegister(header.SourceAddr, pConn, true) {
				pConn.Close()
				return
			}
			defer unregister(header.SourceAddr, pConn)
			safeLog("[Server] Connection from: %s (PPv2)", header.SourceAddr)
			header.DestinationAddr = rawConn.RemoteAddr()
			header.WriteTo(rawConn)
		} else {
			if !checkAndRegister(pConn.RemoteAddr(), pConn, false) {
				pConn.Close()
				return
			}
			defer unregister(pConn.RemoteAddr(), pConn)
			safeLog("[Server] Connection from: %s (Legacy)", zstdConn.RemoteAddr())
		}

		networkIn = pConn
	}

	netReader := &statsReader{Reader: networkIn, counter: &totalZstd}
	netWriter := &statsWriter{Writer: networkOut, counter: &totalZstd}

	enc, _ := zstd.NewWriter(netWriter, zstd.WithEncoderLevel(zstd.EncoderLevelFromZstd(level)))
	dec, _ := zstd.NewReader(netReader)
	defer enc.Close()

	errChan := make(chan error, 2)

	go func() {
		buf := make([]byte, 16*1024)
		for {
			n, err := rawConn.Read(buf)
			if n > 0 {
				atomic.AddUint64(&totalRaw, uint64(n))
				enc.Write(buf[:n])
				enc.Flush()
			}
			if err != nil {
				errChan <- err
				return
			}
		}
	}()

	go func() {
		buf := make([]byte, 16*1024)
		for {
			n, err := dec.Read(buf)
			if n > 0 {
				atomic.AddUint64(&totalRaw, uint64(n))
				rawConn.Write(buf[:n])
			}
			if err != nil {
				errChan <- err
				return
			}
		}
	}()

	<-errChan
}

func main() {
	initLogger()

	mode := flag.String("mode", "server", "Mode: server or client")
	listenAddr := flag.String("l", ":9000", "Listen address")
	remoteAddr := flag.String("r", "127.0.0.1:8080", "Remote address")
	level := flag.Int("L", 3, "Zstd level (1-4)")
	for range 10 {
		fmt.Println("本软件为免费软件,禁止倒卖,如果你为此软件付费了,请马上退款")
	}
	// 安全配置参数 (在此处定义)
	maxConnsPerIP = flag.Int("mc", 20, "Max concurrent connections per IP")
	maxReqInWindow = flag.Int("mr", 30, "Max requests allowed in the time window")
	windowDuration = flag.Duration("wd", 10*time.Second, "Time window for request frequency (e.g., 10s, 1m)")
	banDuration = flag.Duration("bd", 30*time.Minute, "Ban duration for offenders (e.g., 30m, 1h)")

	flag.Parse()

	go func() {
		ticker := time.NewTicker(time.Second)
		var lastRaw, lastZstd uint64
		for range ticker.C {
			currRaw := atomic.LoadUint64(&totalRaw)
			currZstd := atomic.LoadUint64(&totalZstd)
			currConns := atomic.LoadInt64(&activeConns)
			rawSpeed := float64(currRaw-lastRaw) / 1024
			zstdSpeed := float64(currZstd-lastZstd) / 1024
			ratio := 0.0
			if currRaw > 0 {
				ratio = float64(currZstd) / float64(currRaw) * 100
			}
			fmt.Printf("\r[%s] Raw: %s (%.1fKB/s) | Zstd: %s (%.1fKB/s) | Ratio: %.2f%% | Conns: %d          ",
				time.Now().Format("15:04:05"), formatBytes(currRaw), rawSpeed, formatBytes(currZstd), zstdSpeed, ratio, currConns)
			lastRaw, lastZstd = currRaw, currZstd
		}
	}()

	ln, err := net.Listen("tcp", *listenAddr)
	if err != nil {
		log.Fatalf("Fatal: Listen failed: %v", err)
	}

	safeLog("Started [%s] on %s -> %s", *mode, *listenAddr, *remoteAddr)
	safeLog("Security: MaxConns=%d, MaxReq=%d/%v, Ban=%v", *maxConnsPerIP, *maxReqInWindow, *windowDuration, *banDuration)

	for {
		conn, err := ln.Accept()
		if err != nil {
			continue
		}

		go func(c net.Conn) {
			target, err := net.DialTimeout("tcp", *remoteAddr, 5*time.Second)
			if err != nil {
				host, port, _ := net.SplitHostPort(c.RemoteAddr().String())
				safeLog("[%s] Remote %s:%s Connect Error: %v", *mode, host, port, err)
				c.Close()
				return
			}

			if *mode == "server" {
				bridge(target, c, "server", *level)
			} else {
				bridge(c, target, "client", *level)
			}
		}(conn)
	}
}
