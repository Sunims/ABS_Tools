import sys
import os
import re

cmdParameter = sys.argv[1]

val = "maxima -r " + "\"" + cmdParameter + "\""

maximaOutput = os.popen(val).read()
maximaOutput = re.sub('\((%i[0-9]+\)).*\\n', '', maximaOutput)
maximaOutput = re.sub('\\\\\n','', maximaOutput)
startPos = maximaOutput.find("(%o")
maximaOutput = maximaOutput[startPos:]

lines = maximaOutput.split("\n")
for line in lines:
    line = re.sub( '\(%o[0-9]+\)(/R/)?','', line)
    line = re.sub(' ','', line)
    print(line)                  

        

