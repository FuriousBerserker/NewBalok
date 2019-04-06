#!/bin/bash
TOOL=Balok
MODE=ASYNC
THREADS=2
# Extra
ARGS=-agentlib:hprof=heap=sites,format=b
CP=-classpath=benchmarks/sor/original.jar
PATH=./build/bin JVM_ARGS=$ARGS rrrun $CP -tool=$TOOL -offload=$MODE RRBench $THREADS

