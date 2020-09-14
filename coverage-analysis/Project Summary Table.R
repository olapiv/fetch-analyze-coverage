
#######################
# Project summary Table
#######################


# table should have: Org, Project, builds, coverage, SLOC, Previously Studied
summaryData <- na.omit(allData)
summaryData <- unique(summaryData)
minCov <- aggregate(summaryData["CoverageNow"], summaryData["ProjectName"], min)
maxCov <- aggregate(summaryData["CoverageNow"], summaryData["ProjectName"], max)
colnames(minCov) <- c("ProjectName", "Min Coverage")
colnames(maxCov) <- c("ProjectName", "Max Coverage")

buildsCounts <- aggregate(x = summaryData, by = list(unique.values = summaryData$ProjectName), FUN = length)



UniqueName <- unique(summaryData$ProjectName)
UniqueNameDataFrame <- as.data.frame(UniqueName)
colnames(UniqueNameDataFrame) <- c("ProjectName")


SummaryTable <-  merge(
  UniqueNameDataFrame,
  buildsCounts[,1:2],
  by.x = 'ProjectName', 
  by.y = 'unique.values', 
  all.x = TRUE)

SummaryTable <- subset(SummaryTable,childSha>20)  

colnames(SummaryTable) <- c("ProjectName","BuildCounts")

#NEED TO GET THE ORDER BEFORE WE DO THIS FOR REAL
lastValue <- ddply(summaryData,.(ProjectName), tail,1) 

SummaryTable <-  merge(
  SummaryTable,
  lastValue[,c("ProjectName","lang","totalStatementsNow","CoverageNow")],
  by.x = 'ProjectName', 
  by.y = 'ProjectName', 
  all.x = TRUE)

SummaryTable <-  merge(
  SummaryTable,
  minCov,
  by.x = 'ProjectName', 
  by.y = 'ProjectName', 
  all.x = TRUE)

SummaryTable <-  merge(
  SummaryTable,
  maxCov,
  by.x = 'ProjectName', 
  by.y = 'ProjectName', 
  all.x = TRUE)

SummaryTable$PreviouslyStudied <- ""

writeLines(capture.output(
  stargazer(SummaryTable,summary=FALSE,digits=3
            ,dep.var.caption = " "
  )), "../paper/ProjectSummary.tex")

#View(SummaryTable)