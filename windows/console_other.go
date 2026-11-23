//go:build !windows

package main

import "os"

func setupConsole() bool {
	fi, err := os.Stdout.Stat()
	return err == nil && fi.Mode()&os.ModeCharDevice != 0
}

const isWindows = false

func holdWindowOpen() {}
