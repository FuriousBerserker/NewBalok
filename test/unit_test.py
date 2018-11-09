#!/usr/bin/python3
import subprocess
import os
import json
import sys

rootDir = os.path.dirname(os.path.realpath(__file__)) 
testPrefix = 'test'
correctTestPrefix = 'correct'
raceTestPrefix = 'error'
VCFile = 'vc.json'
raceFile = 'race.json'
testClass = 'Test'
sourceFile = testClass + '.java'
targetFile = testClass + '.class'
segment = '****************************************'
success = 0
fail = 0
error = 0
successTests = []
failTests = []
errorTests = []

class VC:

    def __init__(self, tid, local, barrier):
        self.tid = tid
        self.local = local
        self.barrier = barrier

    def __eq__(self, other):
        return other != None and self.tid == other.tid and self.local == other.local and self.barrier == other.barrier

    def __str__(self):
        return 'tid=' + str(self.tid) + ', L=' + self.local + ', B=' + self.barrier

    @staticmethod
    def decodeJson(dic):
        if 'tid' in dic:
            return VC(dic['tid'], dic['local'], dic['barrier'])
        else:
            return dic

    
class Race:

    def __init__(self, type1, type2, access1, access2):
        self.type1 = type1
        self.type2 = type2
        self.access1 = access1
        self.access2 = access2
        self.count = 0

    def __eq__(self, other):
        return other != None and ((self.type1 == other.type1 and self.type2 == other.type2 and self.access1 == other.access1 and self.access2 == other.access2) or (self.type1 == other.type2 and self.type2 == other.type1 and self.access1 == other.access2 and self.access2 == other.access1))

    def __str__(self):
        return 'type1=' + self.type1 + ', type2=' + self.type2 + ', access1=' + self.access1 + ', access2=' + self.access2

    def inc(self):
        self.count += 1

    def getCount(self):
        return self.count

    @staticmethod
    def decodeJson(dic):
        if 'type1' in dic: 
            return Race(dic['type1'], dic['type2'], dic['access1'], dic['access2'])
        else:
            return dic

def checkPythonVersion(major, minor):
    if sys.version_info[0] > major:
        return True
    elif sys.version_info[0] == major and sys.version_info[1] >= minor:
        return True
    else:
        return False

def compileSource(source):
    result = subprocess.run(['javac', source], stdout=subprocess.PIPE, stderr=subprocess.PIPE, encoding='utf-8')
    if result.returncode != 0:
        print('output:')
        print(result.stdout)
        print('error:')
        print(result.stderr)
        return False
    else:
        return True

def runRoadrunner(entryClass):
    result = subprocess.run(['../../build/bin/rrrun', '-tool=Balok', '-unitTest', '-noxml', '-quiet', '-noTidGC', '-offload', entryClass], stdout=subprocess.PIPE, stderr=subprocess.PIPE, encoding='utf-8')
    if result.returncode != 0:
        print('output:')
        print(result.stdout)
        print('error:')
        print(result.stderr)
        return False, None, None
    else:
        return True, result.stdout, result.stderr

def strToVC(inputStr, startPos):
    key1 = 'tid'
    key2 = 'L'
    key3 = 'B'
    value1 = None
    value2 = None
    value3 = None
    separator = ','
    index = startPos
    end = len(inputStr)
    while index < end:
        if inputStr.startswith(key1, index, end):
            index2 = inputStr.index(separator, index + len(key1), end)
            value1 = int(inputStr[index + len(key1) + 1:index2])
            index = index2
            break
        else:
            index += 1

    while index < end:
        if inputStr.startswith(key2, index, end):
            index2 = inputStr.rindex(separator, index + len(key2), end)
            value2 = inputStr[index + len(key2) + 1:index2]
            index = index2
            break
        else:
            index += 1

    while index < end:
        if inputStr.startswith(key3, index, end):
            value3 = inputStr[index + len(key3) + 1:end - 1]
            break
        else:
            index += 1

    vc = VC(value1, value2, value3)
    return vc


    
def checkVC(testCaseName, output):
    prefix = 'TaskTracker'
    success = True
    message = 'checkVC success'
    vcList = []
    if not os.path.isfile(VCFile):
        message = 'checkVC skipped'
        return success, message

    for line in output.splitlines():
        if line.startswith(prefix):
            vc = strToVC(line, len(prefix))
            vcList.append(vc)
    
    expectedVCMap = {}
    with open(VCFile, 'r') as readFile:
        expectedVCs = json.load(readFile, object_hook=VC.decodeJson)
    for expected in expectedVCs:
        expectedVCMap[expected.tid] = expected

    if len(vcList) == len(expectedVCMap):
        for vc in vcList:
            if not vc == expectedVCMap.get(vc.tid):
                if success:
                    success = False
                    message = 'vc does not match, actual: ' + str(vc) + ', expected: ' + str(expectedVCMap.get(vc.tid))
                else:
                    message += ('; actual: ' + str(vc) + ', expected: ' + str(expectedVCMap.get(vc.tid)))
    else:
        success = False
        message = 'The number of threads do not match'
    
    return success, message


def checkRace(testCaseName, output):
    prefix = 'Race Detected'
    accessPrefix = 'Access'
    success = True
    message = 'checkRace success'
    raceList = []
    if not os.path.isfile(raceFile):
        message = 'checkRace skipped'
        return success, message

    lines = iter(output.splitlines())
    while True:
        try:
            line = next(lines)
            if line.startswith(prefix):
                access1 = next(lines)
                while not access1.startswith(accessPrefix):
                    access1 = next(lines)
                info1 = access1.split(' ')
                access2 = next(lines)
                while not access2.startswith(accessPrefix):
                    access2 = next(lines)
                info2 = access2.split(' ')
                #print(len(info1), len(info2))
                #print(info1)
                #print(info2)
                race = Race(info1[4], info2[4], info1[5][:info1[5].rindex(':')], info2[5][:info2[5].rindex(':')])
                raceList.append(race)
        except StopIteration:
            break
    with open(raceFile, 'r') as readFile:
        races = json.load(readFile, object_hook=Race.decodeJson)
    expectedRaceList = []
    for r in races:
        expectedRaceList.append(r)

    for r in raceList:
        try:
            i = expectedRaceList.index(r)
            expectedRaceList[i].inc()
        except ValueError:
            success = False
            message = 'False positive: ' + str(r)
            break

    if success:
        falseNegatives = []
        for r in expectedRaceList:
            if r.getCount() == 0:
                falseNegatives.append(str(r))
        if len(falseNegatives) != 0:
            success = False
            message = 'False negatives:'
            for fn in falseNegatives:
                message += ' '
                message += fn
                
    return success, message

def unitTest(testCaseName):
    global success
    global fail
    global error
    global successTests
    global failTests
    global errorTests
    print('test case:', testCaseName)
    if not os.path.isfile(targetFile):
        compileSuccess = compileSource(sourceFile)
        if not compileSuccess:
            error += 1
            errorTests.append(testCaseName)
            return
            
    executeSuccess, stdout, stderr = runRoadrunner(testClass)
    if executeSuccess:
        passVCChecking, message = checkVC(testCaseName, stdout)
        print(message)
        passRaceChecking, message = checkRace(testCaseName, stdout)
        print(message)
        if passVCChecking and passRaceChecking:
            success += 1
            successTests.append(testCaseName)
        else:
            fail += 1
            failTests.append(testCaseName)
    else:
        error += 1
        errorTests.append(testCaseName)

def main():
    os.chdir(rootDir)
    print('Start unit test for Balok')
    testCases = [o for o in os.listdir(rootDir) if os.path.isdir(os.path.join(rootDir,o)) and o.startswith(testPrefix)]
    print('test cases: ', testCases)
    print(segment)
    for testCase in testCases:
       os.chdir(testCase)
       #print(testCase)
       unitTest(testCase)
       os.chdir('../')
       print(segment)

    if error == 0 and fail == 0:
        print('Pass all test cases')
    else:
        print('Unit test failed')
        print('Error test cases:', errorTests)
        print('Failed test cases:', failTests)

    if checkPythonVersion(3, 6):
        print(f'error: {error}, fail: {fail}, success: {success}' )
    else:
        print('error: {:d}, fail: {:d}, success: {:d}'.format(error, fail, success))
    print('Complete unit test')
   
if __name__ == '__main__':
    main()
