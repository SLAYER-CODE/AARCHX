package main

import (
	"bufio"
	"encoding/json"
	"flag"
	"fmt"
	"net/http"
	"net/url"
	"os"
	"regexp"
	"strings"
	"time"
)

var version string

func getContent(target string, fromto string, match string, filter string) ([]string, error) {
	apiURL := fmt.Sprintf("https://web.archive.org/cdx/search/cdx?url=%s/*&output=json&fl=original&collapse=urlkey", url.QueryEscape(target))
	if fromto != "" {
		apiURL += "&from=" + strings.Split(fromto, ",")[0] + "&to=" + strings.Split(fromto, ",")[1]
	}

	client := &http.Client{Timeout: 60 * time.Second}
	resp, err := client.Get(apiURL)
	if err != nil {
		return nil, fmt.Errorf("request failed: %v", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("bad status: %s", resp.Status)
	}

	var results [][]string
	if err := json.NewDecoder(resp.Body).Decode(&results); err != nil {
		return nil, fmt.Errorf("json decode failed: %v", err)
	}

	var urls []string
	var matchRe, filterRe *regexp.Regexp

	if match != "" {
		matchRe, _ = regexp.Compile(match)
	}
	if filter != "" {
		filterRe, _ = regexp.Compile(filter)
	}

	for _, row := range results {
		if len(row) == 0 {
			continue
		}
		u := row[0]
		if u == "original" {
			continue
		}
		if filterRe != nil && filterRe.MatchString(u) {
			continue
		}
		if matchRe != nil && !matchRe.MatchString(u) {
			continue
		}
		urls = append(urls, u)
	}

	return urls, nil
}

func main() {
	var match, filter, fromto string
	showVersion := flag.Bool("version", false, "show version")
	flag.StringVar(&match, "match", "", "regex pattern to include URLs")
	flag.StringVar(&filter, "filter", "", "regex pattern to exclude URLs")
	flag.StringVar(&fromto, "fromto", "", "date range: from,to (e.g. 20200101,20201231)")
	flag.Parse()

	if *showVersion {
		if version == "" {
			version = "1.0"
		}
		fmt.Printf("wbk version %s\n", version)
		return
	}

	args := flag.Args()
	if len(args) < 1 {
		fmt.Fprintf(os.Stderr, "Usage: wbk [flags] <domain>\n")
		fmt.Fprintf(os.Stderr, "Flags:\n")
		flag.PrintDefaults()
		os.Exit(1)
	}

	scanner := bufio.NewScanner(os.Stdin)
	hasStdin := false
	if stat, _ := os.Stdin.Stat(); stat != nil && stat.Mode()&os.ModeCharDevice == 0 {
		hasStdin = true
	}

	targets := args
	if hasStdin {
		for scanner.Scan() {
			line := strings.TrimSpace(scanner.Text())
			if line != "" {
				targets = append(targets, line)
			}
		}
	}

	for _, target := range targets {
		target = strings.TrimSpace(target)
		if target == "" {
			continue
		}

		urls, err := getContent(target, fromto, match, filter)
		if err != nil {
			fmt.Fprintf(os.Stderr, "error: %s: %v\n", target, err)
			continue
		}

		for _, u := range urls {
			fmt.Println(u)
		}
	}
}
