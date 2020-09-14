library(plyr)
library(reshape2)
library(readr)
library(stargazer)
library(readr)
library(ggplot2)
library(plyr)
library(reshape2)
library(stringr)

basicFormat <- theme(panel.grid.major = element_blank(), panel.grid.minor = element_blank(),
                     plot.title = element_text(hjust = 0.5),
                     axis.title.y=element_blank(),
                     axis.title.x=element_blank(),
                     panel.background = element_blank(), axis.line = element_line(colour = "black"), 
                     legend.key.size=unit(10,"pt"),
                     aspect.ratio=0.5,text=element_text(size=8,  family="Times"))

lightRed <- "#EC564D"
medRed <- "#A03A34"
darkRed <- "#6D2823"

#lightGreen <- "#95B661"
lightGreen <- "#95B661"
medGreen <- "#568C54"
darkGreen <- "#375935"


flappingJacoco <- read_csv("flapping_jacoco.csv")
flappingCoveralls <- read_csv("flapping_coveralls.csv")
flappingAll <- rbind(flappingCoveralls,flappingJacoco)
projSources <- read_csv("project_sources.csv")
coverage <- read_csv("coverage.csv")
# branches <- read_csv("branches_uniq.csv")
# timestamps <- read_csv("ShaAndTime.csv")
projectsCSV <- read_csv("../coveralls_importer/upTo1000PerLang.rand.csv", c("URL", "lang"))
projectsCSV_jacoco <- read_csv("projects_jacoco.csv", c("URL", "lang", "slug"))
shaOrders <- read_csv("csvShaOrder.csv")

coverage_jacoco <- read_csv("coverage_jacoco.csv")
#coverage_jacoco$timeStamp <- as.numeric(strptime(coverage_jacoco$date,'%Y-%m-%d %H:%M:%S'))
projects <- cbind(projectsCSV, colsplit(projectsCSV$URL, ".com/", c("prePend", "ProjectName")))
projects_jacoco <- cbind(projectsCSV_jacoco, colsplit(projectsCSV_jacoco$URL, ".com/", c("prePend", "ProjectName")))

#timestamps <-unique(timestamps)

coverage_jacoco <- merge(projects_jacoco, unique(coverage_jacoco), by.x = 'slug', by.y = 'repo', all.y = TRUE)

#mCov <- coverage[coverage$repo=="ManageIQ/ui-components",]
#mProj <- projects[projects$ProjectName=="ManageIQ/ui-components",]

allData <- merge(projects, coverage, by.x = 'ProjectName', by.y = 'repo', all.y = TRUE)
#allData <- merge(unique(coverage), projects, by.y = 'ProjectName', by.x = 'repo', all.x = TRUE)
#allData <- merge(allData,branches,by.x='childSha',by.y='SHA',all.x = TRUE)
#allData <- merge(allData,timestamps,by.x='childSha',by.y='SHA',all.x = TRUE)
coverage_jacoco$date <- NULL
coverage_jacoco$slug <- NULL
allData$childBranch <- NULL
#coverage_jacoco$branch <- coverage_jacoco$childBranch
coverage_jacoco$childBranch <- NULL
allData <- rbind(allData, coverage_jacoco)

allData <- merge(allData, shaOrders, by.x = 'childSha', by.y = 'sha', all.x = TRUE)
#allData <- allData[allData$branch == 'master',]

allData$ActualChangeToCoverage <- with(allData, (totalStatementsHitNow / totalStatementsNow) - (totalStatementsHitPrev / totalStatementsPrev))
allData$PercentageOfStatementsNewlyCovered <- with(allData, (totalStatementsHitNow + oldLinesNewlyTested) / ((totalStatementsNow + totalStatementsPrev) / 2))
allData$PercentageOfStatementsNoLongerCovered <- with(allData, 0 - (newNonHitLines + oldLinesNoLongerTested) / ((totalStatementsNow + totalStatementsPrev) / 2))
allData$Jaccard <- with(allData, (nStatementsInBoth / nStatementsInEither))
allData$CoverageNow <- with(allData, (totalStatementsHitNow / totalStatementsNow))

allData <- na.omit(allData)

allData <- subset(allData,ProjectName!="Points/PyLCP")
allData <- subset(allData,ProjectName!="apache/commons-configuration")


counts <- data.frame(table(allData$ProjectName))
counts <- counts[counts$Freq>10,]
allData <- merge(allData, counts, by.x = 'ProjectName', by.y = 'Var1', all.y = TRUE)


projectIds <- as.data.frame(unique(allData$ProjectName))
projectIds$pid <- paste("P",str_pad(row.names(projectIds),2,pad="0"), sep="")
projectIds$pid <- as.factor(projectIds$pid)
projectIds$pidNumeric <- as.numeric(row.names(projectIds))
colnames(projectIds) <- c("ProjectName","pid","pidNumeric")
allData <- merge(allData,projectIds,by.x="ProjectName",by.y="ProjectName", all.x=TRUE)
# print(aggregate(completeData$CoverageNow,list(completeData$lang),mean))
# boxplot(completeData$CoverageNow~completeData$lang)


#####################
# StackedBarPlot
#####################

# 
# 
dir.create("plots/StackedBarPlots", showWarnings = FALSE, recursive = TRUE)
plotStackedBar <- function(proj){
    # d <- subset(completeData,ProjectName=="MicroTransactionsMatterToo/midi")
    d <- subset(completeData, ProjectName == proj)
    d$index = nrow(d) - seq.int(nrow(d))
    d$adjustedCoverage <- (d$CoverageNow - d[1, "CoverageNow"]) * 1000

    #rbind( student,
    newHitLines <- d[c("index", "newHitLines")]
    newHitLines$newcol <- rep("HitAdded", nrow(newHitLines))

    newNonHitLines <- d[c("index", "newNonHitLines")]
    newNonHitLines$newcol <- rep("nonHitAdded", nrow(newNonHitLines))

    delTestedLines <- d[c("index", "deletedLinesTested")]
    delTestedLines$newcol <- rep("deletedTestedLines", nrow(delTestedLines))

    delNonTestedLines <- d[c("index", "deletedLinesTested")]
    delNonTestedLines$newcol <- rep("deletedNonTestedLines", nrow(delNonTestedLines))

    oldNewlyTested <- d[c("index", "oldLinesNewlyTested")]
    oldNewlyTested$newcol <- rep("NewlyTestedOld", nrow(oldNewlyTested))

    oldNewlyNotTested <- d[c("index", "oldLinesNoLongerTested")]
    oldNewlyNotTested$newcol <- rep("NewlyNotTestedOld", nrow(oldNewlyNotTested))

    colnames(newHitLines)[2] <- "magnitude"
    colnames(newNonHitLines)[2] <- "magnitude"
    colnames(delTestedLines)[2] <- "magnitude"
    colnames(delNonTestedLines)[2] <- "magnitude"
    colnames(oldNewlyTested)[2] <- "magnitude"
    colnames(oldNewlyNotTested)[2] <- "magnitude"

    allInc <- rbind(newHitLines, delNonTestedLines, oldNewlyTested)
    allDec <- rbind(newNonHitLines, delTestedLines, oldNewlyNotTested)
    allDec$magnitude <- allDec$magnitude * - 1
    d_coverage <- subset(d, select = c("index", "CoverageNow"))

    allInc_transformed <- allInc
    allInc_merged <- merge(x = allInc, y = d_coverage, by = "index", all.y = TRUE)

    ggplot(d, aes(x = index)) +
    #geom_bar(aes(y=((magnitude/10000)+d[1,"CoverageNow"]), fill=newcol), stat="identity",data=allInc) +
    #geom_bar(aes(y=((magnitude/10000)+d[1,"CoverageNow"]), fill=newcol), stat="identity",data=allDec)+
    #geom_bar(aes(y=(magnitude), fill=newcol), stat="identity",data=allInc)+
        geom_bar(aes(y = magnitude, fill = newcol), stat = "identity", data = allInc) +
        geom_bar(aes(y = magnitude, fill = newcol), stat = "identity", data = allDec) +
        geom_line(aes(y = totalStatementsNow), color = "blue", data = d) +
        geom_line(aes(y = totalStatementsHitNow), color = "green", data = d) +
        geom_line(aes(y = CoverageNow * 1000), color = "orange", data = d) +
        scale_y_continuous(sec.axis = sec_axis(~ . * 5, name = "size of change")) +
        labs(y = "Code Coverage (%)", x = "Builds", colour = "Parameter")
    ggsave(paste("plots/StackedBarPlots/", gsub("/", "-", proj), ".pdf", sep = ""), device = "pdf")
}

# plot all
#lapply(unique(completeData$ProjectName),plotStackedBar)
# plot one
#plotStackedBar("MicroTransactionsMatterToo/midi")





#Calculate sum of green and red vs blue



#####################
# LINES ADDED AND DELETED
#####################

dir.create("plots/diffChanges", showWarnings = FALSE, recursive = TRUE)

diffChanges <- function(proj){
    completeData <- subset(completeData, ProjectName == proj)
    #completeData <- subset(completeData,ProjectName=="Axxiss/driving-time")
    completeData$index = nrow(completeData) - seq.int(nrow(completeData))
    diffData <- completeData

    diffData$diffInc <- diffData$newHitLines
    diffData$diffDec <- (diffData$newNonHitLines) * - 1

    diffData$prevInc <- diffData$oldLinesNewlyTested
    diffData$prevDec <- (diffData$oldLinesNoLongerTested) * - 1


    ggplot(diffData, aes(x = index, fill = childSha)) +
        geom_bar(aes(y = diffInc, fill = "diff Inc Lines"), stat = "identity", data = diffData) +
        geom_bar(aes(y = diffDec, fill = "diff Dec Lines"), stat = "identity", data = diffData) +
        geom_bar(aes(y = prevInc, fill = "prev Inc Lines"), stat = "identity", data = diffData) +
        geom_bar(aes(y = prevDec, fill = "prev Dec Lines"), stat = "identity", data = diffData) +
        ggsave(paste("plots/diffChanges/", gsub("/", "-", proj), ".pdf", sep = ""), device = "pdf")
}

#lapply(unique(completeData$ProjectName),diffChanges)


#####################
# COV AND PREV PERCENT
#####################


dir.create("plots/split", showWarnings = FALSE, recursive = TRUE)

# CovPercentsChanges <- function(proj){
#   completeData <- subset(completeData,ProjectName==proj)
#   #completeData <- subset(completeData,ProjectName=="Axxiss/driving-time")
#   # completeData$index = nrow(completeData)-seq.int(nrow(completeData))
#   # diffData <- completeData
#   # 
#   # diffData$diffInc <- diffData$newHitLines
#   # diffData$diffDec <- (diffData$newNonHitLines)*-1
#   # 
#   # diffData$prevInc <-diffData$oldLinesNewlyTested
#   # diffData$prevDec <- (diffData$oldLinesNoLongerTested)*-1
#   # 
#   
#   # ggplot(diffData,aes(x=index,fill=childSha)) + 
#   #   geom_bar(aes(y=diffInc,fill="diff Inc Lines"), stat="identity",data=diffData)+
#   #   geom_bar(aes(y=diffDec,fill="diff Dec Lines"), stat="identity",data=diffData)+
#   #   geom_bar(aes(y=prevInc,fill="prev Inc Lines"), stat="identity",data=diffData)+
#   #   geom_bar(aes(y=prevDec,fill="prev Dec Lines"), stat="identity",data=diffData)
#   #  # ggsave(paste("plots/diffChanges/",gsub("/", "-", proj),".pdf",sep=""),device="pdf")
#   
# }

#sCovPercentsChanges("andaru-afind")
splitChanges <- function(proj){
    # covSplitData <- subset(completeData,ProjectName=="AaronTheApe/distne")
    covSplitData <- subset(completeData, ProjectName == proj)
    covSplitData$index <- nrow(covSplitData) - seq.int(nrow(covSplitData))

    covSplitData$allNewHits <- covSplitData$newHitLines + covSplitData$newFileHitLines
    covSplitData$allNewMisses <- covSplitData$newNonHitLines + covSplitData$newFileNonHitLines
    # covSplitData$allDeletedHit <- covSplitData$deletedLinesTested + covSplitData$deletedFileLinesTested
    # covSplitData$allDeletedNitHit <- covSplitData$deletedLinesNotTested + covSplitData$deletedFileLinesNotTested
    # covSplitData$prevHit <-
    # covSplitData$prevMiss <-

    covSplitData$diffCov <- covSplitData$allNewHits / (covSplitData$allNewHits + covSplitData$allNewMisses)
    covSplitData$prevCov <- covSplitData$allNewHits / (covSplitData$allNewHits + covSplitData$allNewMisses)

    covSplitData$prevHits <- (covSplitData$totalStatementsHitNow - covSplitData$allNewHits)
    covSplitData$prevTotal <- (covSplitData$totalStatementsNow - (covSplitData$allNewHits + covSplitData$allNewMisses))

    covSplitData$prevCov <- covSplitData$prevHits / covSplitData$prevTotal

    covGraph <- covSplitData[, c(34, 35, 30)]
    covGraph$index <- seq.int(nrow(covGraph))


    ggplot(covGraph, aes(x = index)) +
        geom_bar(aes(y = diffCov, fill = "diff Inc Lines"), stat = "identity", data = covGraph, position = "dodge") +
        geom_bar(aes(y = prevCov, fill = "diff Prev Lines"), stat = "identity", data = covGraph, position = "dodge") +
        geom_bar(aes(y = CoverageNow, fill = "total Lines"), stat = "identity", data = covGraph, position = "dodge")


    # df <- read.table(text = "       Input Rtime Rcost Rsolutions  Btime Bcost
    # 1   12-proc.     1    36     614425     40    36
    #                  2   15-proc.     1    51     534037     50    51
    #                  3    18-proc     5    62    1843820     66    66
    #                  4    20-proc     4    68    1645581 104400    73
    #                  5 20-proc(l)     4    64    1658509  14400    65
    #                  6    21-proc    10    78    3923623 453600    82",header = TRUE,sep = "")

    covGraphM <- melt(covGraph[, c('index', 'diffCov', 'prevCov', 'CoverageNow')], id.vars = 1)
}

#lapply(unique(completeData$ProjectName),splitChanges)


#####################
# Total LINES
#####################

dir.create("plots/total", showWarnings = FALSE, recursive = TRUE)
totalChanges <- function(proj){

    completeData <- subset(completeData, ProjectName == proj)

    completeData <- completeData[order(completeData$idx),]

    completeData$index = seq.int(nrow(completeData))
    completeData$missedLines <- with(completeData, totalStatementsNow - totalStatementsHitNow)
    completeData$totalUp <- with(completeData, newHitLines +
        deletedLinesNotTested +
        oldLinesNewlyTested)
    completeData$totalDown <- with(completeData, newNonHitLines +
        deletedLinesTested +
        oldLinesNoLongerTested)
    completeData$rawCoverageChange <- with(completeData, totalStatementsHitNow - totalStatementsHitPrev)
    completeData$rawSizeChange <- with(completeData, totalStatementsNow - totalStatementsPrev)

    totalData <- completeData
    # totalData <- subset(totalData, totalStatementsNow < 5000)
    # occludedData <- subset(occludedData, ProjectName ==  "MicroTransactionsMatterToo/midi")
    # completeDatasubset(expr, cell_type == "hesc")

    totalData$changeInLinesCovered <- totalData$totalStatementsHitNow - totalData$totalStatementsHitPrev

    #occludedData$increaseCovg <- occludedData$newHitLines + occludedData$oldLinesNewlyTested + occludedData$deletedLinesNotTested
    #occludedData$decreasedCovg <- (occludedData$newNonHitLines + occludedData$oldLinesNoLongerTested + occludedData$deletedLinesTested)*-1

    totalData$increaseCovg <- totalData$deletedFileLinesNotTested +
        totalData$newHitLines +
        totalData$oldLinesNewlyTested
    totalData$decreasedCovg <- (totalData$deletedFileLinesTested +
        totalData$oldLinesNoLongerTested +
        totalData$deletedLinesTested) * - 1
    #
    #   totalData$increaseCovg <- totalData$newHitLines + totalData$oldLinesNewlyTested
    #   totalData$decreasedCovg <- (totalData$oldLinesNoLongerTested + totalData$deletedLinesTested)*-1
    #

    # 
    # print(ProjectName)
    if (length(totalData) > 5) {
        # print(proj)
        ggplot(totalData, aes(x = index)) +
            ggtitle(proj) +
            geom_bar(aes(y = totalStatementsNow, fill = "totalLines"), stat = "identity", data = totalData) +
            geom_bar(aes(y = totalStatementsHitNow, fill = "hit"), stat = "identity", data = totalData) +
            geom_bar(aes(y = changeInLinesCovered, fill = "changeInLinesCovered"), stat = "identity", data = totalData) +
            geom_bar(aes(y = increaseCovg, fill = "increaseCovg"), stat = "identity", data = totalData) +
            geom_bar(aes(y = decreasedCovg, fill = "decreasedCovg"), stat = "identity", data = totalData)
        # ggsave(paste("plots/total/",gsub("/", "-", proj),".pdf",sep=""),device="pdf")
    }
}

#plot all jacoco
#jacocoOnly <- subset(completeData, lang == "jacoco")
#lapply(unique(jacocoOnly$ProjectName),totalChanges)
# plot all
#lapply(unique(completeData$ProjectName),totalChanges)
# plot one
# totalChanges("lauramcastro/Erlang_Architecture_Behaviours")
#totalChanges("plasmap/geow")
#totalChanges("damianszczepanik/cucumber-reporting")



 

