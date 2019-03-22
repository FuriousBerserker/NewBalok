set -e
set -x

ROOT=$(dirname $(readlink -f "$0"))
CURRENT=$(pwd)
BMS="crypt:lufact:moldyn:montecarlo:raytracer:series:sor:sparsematmult:avrora:batik:fop:h2:jython:luindex:lusearch:pmd:sunflow:tomcat:xalan"
export PATH="${ROOT}/../build/bin:${PATH}"

cd ${ROOT}/../benchmarks
echo $BMS | tr ':' '\n' | while read bm; do
    cd $bm
    echo " " $bm
    logFolder="${bm}"
    ./TEST_BENCH -tool=Balok -noxml -quiet -noTidGC -offload=ASYNC -outputAccess -folder=${logFolder} 2>&1
    mv ${logFolder} ${CURRENT}/
    cd ..
done
