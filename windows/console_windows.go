//go:build windows

package main

import (
	"syscall"
	"unsafe"
)

// Lets the Windows console show colours, ticks and arrows, and keeps the window open at the end
// when SimpleScript.exe was opened by double-clicking.
func setupConsole() bool {
	k := syscall.NewLazyDLL("kernel32.dll")
	k.NewProc("SetConsoleOutputCP").Call(65001)
	k.NewProc("SetConsoleCP").Call(65001)
	h, err := syscall.GetStdHandle(syscall.STD_OUTPUT_HANDLE)
	if err != nil {
		return false
	}
	var mode uint32
	if syscall.GetConsoleMode(h, &mode) != nil {
		return false
	}
	const enableVT = 0x0004
	r, _, _ := k.NewProc("SetConsoleMode").Call(uintptr(h), uintptr(mode|enableVT))
	return r != 0
}

const isWindows = true

func holdWindowOpen() {
	// Opened by double-clicking: this is the only program in the console window, which would
	// vanish before the last message could be read. From Command Prompt or PowerShell it stays.
	var ids [4]uint32
	n, _, _ := syscall.NewLazyDLL("kernel32.dll").NewProc("GetConsoleProcessList").
		Call(uintptr(unsafe.Pointer(&ids[0])), 4)
	if n <= 1 {
		say("")
		readLine("  Press Enter to close this window.")
	}
}
