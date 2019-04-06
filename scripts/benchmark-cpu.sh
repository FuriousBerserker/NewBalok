#!/bin/bash
#TOOL=FT2
TOOL=Balok
MODE=SYNC
THREADS=2
ARGS="-agentlib:hprof=cpu=samples,depth=8,interval=5 -Xmx8g"
CP=benchmarks/sor/original.jar
PATH=./build/bin JVM_ARGS=$ARGS rrrun -classpath=$CP -tool=$TOOL -offload=$MODE RRBench $THREADS
