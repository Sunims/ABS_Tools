import sys
import os
import re

cmdParameter = sys.argv[1]

val = "maxima -r " + "\"" + cmdParameter + "\""

maximaOutput = os.popen(val).read()
maximaOutput = re.sub('\((%i[0-9]+\)).*\\n', '', maximaOutput)
maximaOutput = re.sub('\\\\\n','', maximaOutput)

lines = maximaOutput.split("\n")
headerLineCount = 4
lineCount = 0
for line in lines:
    if lineCount >  headerLineCount :   
        line = re.sub( '\(%o[0-9]+\)(/R/)?','', line)
        line = re.sub(' ','', line)
        if line :
            print(line)
    lineCount = lineCount+1

        

