dir.create("plots/total", showWarnings = FALSE, recursive = TRUE)
dir.create("plots/total", showWarnings = FALSE, recursive = TRUE)
totalChanges <- function(proj){

    completeData <- subset(completeData, ProjectName == proj)
    completeData <- completeData[! duplicated(completeData[, c('parentSha')]),]

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
        print(proj)
        stupidPlot <- ggplot(totalData, aes(x = index)) +
            ggtitle(proj) +
            geom_rect(mapping = aes(xmin = index, xmax = index + 1, ymin = 0, ymax = totalStatementsNow), fill = "darkgrey") +
            geom_rect(mapping = aes(xmin = index, xmax = index + 1, ymin = 0, ymax = totalStatementsHitNow), fill = "lightgreen") +
        # geom_bar(aes(y=totalStatementsNow,fill="totalLines"), stat="identity",data=totalData)+
        # geom_bar(aes(y=totalStatementsHitNow,fill="hit"), stat="identity",data=totalData)+

        #change in the set of lines covered?
            geom_rect(fill = "darkgreen", alpha = 0.85, mapping = aes(xmin = index, xmax = index + 1, ymin = totalStatementsHitNow, ymax = totalStatementsHitNow + increaseCovg)) +
            geom_rect(fill = "darkred", alpha = 0.85, mapping = aes(xmin = index, xmax = index + 1, ymin = totalStatementsHitNow, ymax = totalStatementsHitNow + decreasedCovg)) +
            geom_rect(color = "black", alpha = 0, mapping = aes(xmin = index, xmax = index + 1, ymin = totalStatementsHitNow, ymax = totalStatementsHitNow + changeInLinesCovered)) +
        # geom_bar(aes(y=totalStatementsHitNow - decreasedCovg,fill="decreasedCovg"), stat="identity",data=totalData)+
            coord_cartesian(ylim = c(min(totalData$totalStatementsHitNow + totalData$decreasedCovg) - 20, max(totalData$totalStatementsHitNow + totalData$increaseCovg) + 20))

        ggsave(paste("plots/total/", gsub("/", "-", proj), ".pdf", sep = ""), device = "pdf")
        #print(stupidPlot)
    }
}


#plot all jacoco
jacocoOnly <- subset(completeData, lang == "jacoco")
lapply(unique(jacocoOnly$ProjectName), totalChanges)
# plot all
#lapply(unique(completeData$ProjectName),totalChanges)
# plot one
# totalChanges("lauramcastro/Erlang_Architecture_Behaviours")
#totalChanges("plasmap/geow")
# totalChanges("apache/empire-db")
