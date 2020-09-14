library(sparkline)
library(xtable)
library(sparktex)
library(dplyr)
library(parallel)


coverageSparkline<- function (ipid){
  sparktex(subset(allData,pidNumeric==ipid)$CoverageNow, cat=paste("../paper/coverage_sparklines/",ipid,".tex",sep = ""),
           rectangle=list(c(0,1,"rgb","0.84,0.93,0.96")))
}
formatPercent <- function(d){
  paste("$",round(d),"\\%$",sep="")
}
#View(allData)

# \textbf{TODO}~RQ2: How many patches touch only code, only tests,
# none, or both? Are most code patches accompanied
# by a new or modified test case? How many patches
# modify neither executable code nor tests?
# %same as above
# % percent commits touching tests/ touching source/ touching both /none 
# % Table - To be built (also put size of each patch for RQ3)
# 
# \textbf{DO}~RQ3: What is the distribution of patch sizes? How
# spread out is each patch through the code? Are
# most patches small? How many different parts of the
# code does a patch touch? What is the median number
# of lines, hunks and files affected by a patch?
# % size of patch, # of files touched per patch Add to table for RQ2



#allData$totalPatchSize <- allData$newHitLines + allData$newNonHitLines + allData$newFileHitLines + allData$newFileNonHitLines + 
#  allData$deletedLinesTested + allData$deletedLinesNotTested + allData$deletedFileLinesTested + allData$deletedFileLinesNotTested

allData$srcPatchSize <- allData$newLinesSrc+allData$delLinesSrc
  
  #allData$src_newHitLines + allData$src_newNonHitLines + allData$src_newFileHitLines + allData$src_newFileNonHitLines + 
  #allData$src_deletedLinesTested + allData$src_deletedLinesNotTested + allData$src_deletedFileLinesTested + allData$src_deletedFileLinesNotTested

allData$testPatchSize <- allData$newLinesTest+allData$delLinesTest
  #allData$test_newHitLines + allData$test_newNonHitLines + allData$test_newFileHitLines + allData$test_newFileNonHitLines + 
  #allData$test_deletedLinesTested + allData$test_deletedLinesNotTested + allData$test_deletedFileLinesTested + allData$test_deletedFileLinesNotTested



totalNonZeroSizePatches <- nrow(allData)-as.data.frame(table(allData$totalPatchSize))[1,2]

totalNonZeroSizeSrcPatches <- nrow(allData)-as.data.frame(table(allData$srcPatchSize))[1,2]

totalNonZeroSizeTestPatches <- nrow(allData)-as.data.frame(table(allData$testPatchSize))[1,2]


allTotalPatches <- allData#[allData$totalPatchSize>0,]
allSrcPatches <- allData[allData$srcPatchSize>0,]
allTestPatches <- allData[allData$testPatchSize>0,]

allData$numberOfFilesTouched <- allData$insFilesSrc + allData$insFilesTest + allData$modFilesSrc + allData$modFilesTest + allData$delFilesSrc + allData$delFilesTest
allData$srcFilesTouched <- allData$insFilesSrc + allData$modFilesSrc + allData$delFilesSrc 
allData$testFilesTouched <-   allData$insFilesTest + allData$modFilesTest + allData$delFilesTest



# allData[, length(totalPatchSize>0), by = ProjectName]

countPatches <- with( allData[allData$totalPatchSize > 0,], table(ProjectName) )
countSrcEdits <- with( allData[allData$srcFilesTouched > 0,], table(ProjectName) )
countTestEdits <- with( allData[allData$testFilesTouched > 0,], table(ProjectName) )


#percent commits touching tests/ touching source/ touching both /none 
allData$onlyTests <- ifelse(allData$testFilesTouched > 0  & allData$srcFilesTouched < 1,1,0)
allData$onlySrc <- ifelse(allData$testFilesTouched <1  & allData$srcFilesTouched >0,1,0)
allData$both <- ifelse(allData$testFilesTouched >0  & allData$srcFilesTouched >0,1,0)
allData$neither <- ifelse(allData$testFilesTouched <1  & allData$srcFilesTouched <1,1,0)

countOnlyTests <- aggregate(allData$onlyTests,list(allData$ProjectName),sum)
countonlySrc <- aggregate(allData$onlySrc,list(allData$ProjectName),sum)
countboth <- aggregate(allData$both,list(allData$ProjectName),sum)
countneither <- aggregate(allData$neither,list(allData$ProjectName),sum)
avgCoverage <- aggregate(allData$CoverageNow,list(allData$ProjectName),mean)
firstCoverage <- aggregate(allData$CoverageNow,list(allData$ProjectName),first)
lastCoverage <- aggregate(allData$CoverageNow,list(allData$ProjectName),last)

projLangs <- aggregate(allData$lang,list(allData$ProjectName),FUN=head,1)
totalStatements <- aggregate(allData$totalStatementsNow,list(allData$ProjectName),mean)
minTimes <- aggregate(allData$timestamp,list(allData$ProjectName),min)
maxTimes <- aggregate(allData$timestamp,list(allData$ProjectName),max)

colnames(countOnlyTests) <- c("ProjectName", "testCount")
colnames(countonlySrc) <- c("ProjectName", "srcCount")
colnames(countboth) <- c("ProjectName", "bothCount")
colnames(countneither) <- c("ProjectName", "neitherCount")
colnames(avgCoverage) <- c("ProjectName", "avgCoverage")
colnames(firstCoverage) <- c("ProjectName", "firstCoverage")
colnames(lastCoverage) <- c("ProjectName", "lastCoverage")
colnames(projLangs) <- c("ProjectName", "Language")
colnames(totalStatements) <- c("ProjectName", "totalStatements")
colnames(minTimes) <- c("ProjectName", "minTimestamp")
colnames(maxTimes) <- c("ProjectName", "maxTimestamp")



allProjects <- as.data.frame(table(allData$ProjectName))
colnames(allProjects) <- c("ProjectName", "totalRows")
allProjects <- merge(allProjects, countOnlyTests, by.x = 'ProjectName', by.y = 'ProjectName', all.y = TRUE)
allProjects <- merge(allProjects, countonlySrc, by.x = 'ProjectName', by.y = 'ProjectName', all.y = TRUE)
allProjects <- merge(allProjects, countboth, by.x = 'ProjectName', by.y = 'ProjectName', all.y = TRUE)
allProjects <- merge(allProjects, countneither, by.x = 'ProjectName', by.y = 'ProjectName', all.y = TRUE)
allProjects <- merge(allProjects, avgCoverage, by.x = 'ProjectName', by.y = 'ProjectName', all.y = TRUE)
allProjects <- merge(allProjects, firstCoverage, by.x = 'ProjectName', by.y = 'ProjectName', all.y = TRUE)
allProjects <- merge(allProjects, lastCoverage, by.x = 'ProjectName', by.y = 'ProjectName', all.y = TRUE)
allProjects <- merge(allProjects, projectIds, by.x = 'ProjectName', by.y = 'ProjectName', all.y = TRUE)
allProjects <- merge(allProjects, projLangs, by.x = 'ProjectName', by.y = 'ProjectName', all.y = TRUE)
allProjects <- merge(allProjects, totalStatements, by.x = 'ProjectName', by.y = 'ProjectName', all.y = TRUE)
allProjects <- merge(allProjects, minTimes, by.x = 'ProjectName', by.y = 'ProjectName', all.y = TRUE)
allProjects <- merge(allProjects, maxTimes, by.x = 'ProjectName', by.y = 'ProjectName', all.y = TRUE)

allProjects$Language <- ifelse(allProjects$Language =="jacoco","java",paste(allProjects$Language))
allProjects$dayRange <- as.integer(round((allProjects$maxTimestamp - allProjects$minTimestamp)/(60*60*24*30),0))

allProjects$percentTest <- (allProjects$testCount/allProjects$totalRows*100)
allProjects$percentSrc <- (allProjects$srcCount/allProjects$totalRows*100)
allProjects$percentboth <- (allProjects$bothCount/allProjects$totalRows*100)
allProjects$percentneither <- (allProjects$neitherCount/allProjects$totalRows*100)
allProjects$totalStatements <- prettyNum(as.integer(allProjects$totalStatements),big.mark=",")
#allProjects$sumPercent <- allProjects$percentTest + allProjects$percentSrc + allProjects$percentboth + allProjects$percentneither
avgPatchSizeSrc <- aggregate(allData$srcPatchSize,list(allData$ProjectName),mean) 
avgPatchSizeTest <- aggregate(allData$testPatchSize,list(allData$ProjectName),mean) 
avgFilesTouched <- aggregate(allData$numberOfFilesTouched,list(allData$ProjectName),mean)
avgPatchSizeAllLines<- aggregate(allData$insLinesAllFiles+allData$delLinesAllFiles,list(allData$ProjectName),mean)


colnames(avgPatchSizeTest) <- c("ProjectName", "avgPatchSizeTest")
colnames(avgPatchSizeSrc) <- c("ProjectName", "avgPatchSizeSrc")
colnames(avgFilesTouched) <- c("ProjectName", "avgFilesTouched")
colnames(avgPatchSizeAllLines) <- c("ProjectName", "avgPatchSizeAllLines")

allProjects <- merge(allProjects, avgPatchSizeSrc, by.x = 'ProjectName', by.y = 'ProjectName', all.x = TRUE)
allProjects <- merge(allProjects, avgPatchSizeTest, by.x = 'ProjectName', by.y = 'ProjectName', all.x = TRUE)
allProjects <- merge(allProjects, avgPatchSizeAllLines, by.x = 'ProjectName', by.y = 'ProjectName', all.x = TRUE)
allProjects <- merge(allProjects, projSources, by.x = 'ProjectName', by.y = 'ProjectName', all.x = TRUE)
allProjects$Source <- ifelse(is.na(allProjects$Source),"\\CIO",allProjects$Source)

allProjects$Coverage <- paste("\\input{coverage_sparklines/",allProjects$pidNumeric,"}", sep="")

allProjects$avgPatchSizeSrc <- as.integer(allProjects$avgPatchSizeSrc)
allProjects$avgPatchSizeTest <- as.integer(allProjects$avgPatchSizeTest)
allProjects$avgPatchSizeAllLines <- as.integer(allProjects$avgPatchSizeAllLines)

row.names(allProjects) <- allProjects$pid

#print(allProjectsTable)
tot<-c(paste("\\midrule \\multicolumn{2}{l}{\\textbf{Total ", 
             nrow(allProjects)," projects, ",
             prettyNum(as.integer(sum(totalStatements$totalStatements)),big.mark=",")," LOC}}",sep=""),
       "\\multicolumn{2}{r}{\\textbf{Average:}}",
       as.integer(mean(allProjects$totalRows)),
       prettyNum(as.integer(mean(totalStatements$totalStatements)),big.mark=","),
       as.integer(mean(allProjects$dayRange)),
       formatPercent(mean(allProjects$firstCoverage)*100),
       " ",
       formatPercent(mean(allProjects$lastCoverage)*100),
       formatPercent(mean(allProjects$percentTest)),
       formatPercent(mean(allProjects$percentSrc)),
       formatPercent(mean(allProjects$percentboth)),
       formatPercent(mean(allProjects$percentneither)),
       as.integer(mean(allProjects$avgPatchSizeSrc)),
       as.integer(mean(allProjects$avgPatchSizeTest)),
       as.integer(mean(allProjects$avgPatchSizeAllLines))
       )

allProjects$avgPatchSizeSrc <- prettyNum(allProjects$avgPatchSizeSrc, big.mark=",")
allProjects$avgPatchSizeTest <- prettyNum(allProjects$avgPatchSizeTest, big.mark=",")
allProjects$avgPatchSizeAllLines <- prettyNum(allProjects$avgPatchSizeAllLines, big.mark=",")
allProjects$percentSrc <- formatPercent(allProjects$percentSrc)
allProjects$percentTest <- formatPercent(allProjects$percentTest)
allProjects$percentboth <- formatPercent(allProjects$percentboth)
allProjects$percentneither <- formatPercent(allProjects$percentneither)
allProjects$firstCoverage <- formatPercent(allProjects$firstCoverage*100)
allProjects$lastCoverage <- formatPercent(allProjects$lastCoverage*100)
allProjectsTable <- allProjects[,c("ProjectName","Language","Source","totalRows","totalStatements","dayRange","firstCoverage","Coverage","lastCoverage","percentTest","percentSrc","percentboth","percentneither","avgPatchSizeSrc","avgPatchSizeTest","avgPatchSizeAllLines")]
# names(tot) <- c("ProjectName","totalRows","Language","Source","percentTest","percentSrc","percentboth","percentneither","avgPatchSizeSrc","avgPatchSizeTest","avgPatchSizeAllLines","totalStatements","Coverage")
tot <- data.frame(t(tot))
# allProjectsTable <- rbind(allProjectsTable,tot)
print(xtable(allProjectsTable),only.contents=TRUE,sanitize.text.function = function(x) {x},type="latex",file="../paper/PatchSizeSummary.tex",include.colnames = FALSE,hline.after=c())
print(xtable(tot),append=TRUE,include.rownames=FALSE,only.contents=TRUE,sanitize.text.function = function(x) {x},type="latex",file="../paper/PatchSizeSummary.tex",include.colnames = FALSE,hline.after=c())


# \textbf{TODO}~RQ2: How many patches touch only code, only tests,
# none, or both? Are most code patches accompanied
# by a new or modified test case? How many patches
# modify neither executable code nor tests?
# %same as above
# % percent commits touching tests/ touching source/ touching both /none 
# % Table - To be built (also put size of each patch for RQ3)
# 
# \textbf{DO}~RQ3: What is the distribution of patch sizes? How
# spread out is each patch through the code? Are
# most patches small? How many different parts of the
# code does a patch touch? What is the median number
# of lines, hunks and files affected by a patch?
# % size of patch, # of files touched per patch Add to table for RQ2

# patches touch only code, #patches touch only tests, # patches touch both, # patches touch none
# size of patch in statement lines, # of files per patch

mclapply(unique(allData$pidNumeric), coverageSparkline)
# coverageSparkline(5)



