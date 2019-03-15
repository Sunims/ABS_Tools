import sys
import os
import re
import glob

numberOfFunctions =int(sys.argv[1])
yStart=sys.argv[2]
yEnd=sys.argv[3]

fileCounter = 0
for filepath in glob.glob("output*"):
    fileCounter +=1 
    with open(filepath) as f:
        lines = f.readlines()
    f.close()
    
    preparedLines = []
    for line in lines:
        line = line.replace('\\','') 
        matchObj = re.match( '(\{|")(.*)(\}|")', line)    
        if matchObj:        
            tmpLine = re.sub(' ', '', matchObj.group(2))
            tmpLine = re.sub(',', '/', tmpLine)
            preparedLines.append(tmpLine)

    #---------------------------------------
    #Concat functions 
    #---------------------------------------

    # start time
    t=[]
    t.append("t_" + str(fileCounter) + "_0" + ":" + preparedLines.pop(0) + ";")
    
    functions = []
    rows = int(len(preparedLines)/(numberOfFunctions+1))
    for i in range(0,numberOfFunctions):
        functions.append(["F_" + str(fileCounter) + "_" + str(i+1) + "(t):="])
        
    functions.append([])
    print (str(rows)) 
    for i in range(0,rows):          
        t.append("t_" + str(fileCounter) + "_" + str(i+1) + ":" + preparedLines[(i*(numberOfFunctions+1))+numberOfFunctions] + ";")
        if i>0:    
            for j in range(0,numberOfFunctions):
                functions[j].append(" else ")    
        
        if i<rows-1:
            for j in range(0,numberOfFunctions):
                functions[j].append("if t<t_" + str(fileCounter) + "_" + str(i+1) + " then ")
            
        for j in range(0,numberOfFunctions):
            functions[j].append(preparedLines[(i*(numberOfFunctions+1))+j])
    
    print functions
    maximaScript = ""
    for i in range(0,numberOfFunctions):
        maximaScript = maximaScript + ''.join(functions[i]) + ";"   
        
    maximaScript = ''.join(t) + ''.join(maximaScript)    
    
    #---------------------------------------
    # generate plott command 
    #---------------------------------------
    plottStatement = "plot2d(["
    for i in range(0,numberOfFunctions):
        plottStatement +=  "F_" + str(fileCounter) + "_" + str(i+1) + "(t)"
        
        if i < numberOfFunctions-1:
            plottStatement += ","
    
    plottStatement += "], [t," + "t_" + str(fileCounter) + "_0" + ",t_" + str(fileCounter) + "_" + str(rows) + "],[y," + yStart + "," + yEnd + "], [plot_format, gnuplot]);"
    maximaScript = maximaScript + plottStatement    
    
    val = "maxima -r " + "'" + maximaScript + "quit();'"
    os.system(val)
    
    #---------------------------------------
    # save output in file 
    #---------------------------------------    
    outputFileName = "m_" + os.path.splitext(os.path.basename(f.name))[0]
    outputFileDir = os.path.dirname(f.name)
    
    outputFile = open(outputFileName, "w")
    outputFile.write(maximaScript)
    outputFile.close()
    #os.remove(f.name) 

    



    


    
