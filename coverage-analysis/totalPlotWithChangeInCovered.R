require(parallel)

dir.create("plots/changedLines", showWarnings = FALSE, recursive = TRUE)
totalChanges <- function(proj, saveToDisk){

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
    totalData$AbschangeInLinesCovered <- abs(totalData$totalStatementsHitNow - totalData$totalStatementsHitPrev)
    totalData$changeInSetOfLinesCovered <- totalData$nStatementsInEither - totalData$nStatementsInBoth

    #  totalData$coverageToUseForGreenBar <- ifelse()

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
    if (max(totalData$idx > 10)) {
        print(proj)

        cols <- c("Statements Hit" = "#BAFFA1", "Statements whose coverage changed" = "#161CDE", "Coverage" = "#722906")#,"Decrease" = "#161CDE", "Increase"="#BAFFA1")
        yRange <- max(totalData$totalStatementsHitNow + totalData$increaseCovg) - min(totalData$totalStatementsHitNow + totalData$decreasedCovg)
        if (yRange == 0)
        yRange = 1
        totalData$coverageScaled <- min(totalData$totalStatementsHitNow + totalData$decreasedCovg) + (yRange * totalData$totalStatementsHitNow / totalData$totalStatementsNow)

        print(max(totalData$totalStatementsHitNow))
        print(min(totalData$totalStatementsHitNow))

        stupidPlot <- ggplot(totalData, aes(x = index)) +
            ggtitle(proj) +
        # geom_rect(mapping=aes(xmin=index,xmax=index+1,ymin=0,ymax=totalStatementsNow),fill="darkgrey")+
            geom_rect(mapping = aes(xmin = index, xmax = index + 1, ymin = 0, ymax = totalStatementsHitNow, fill = "Statements Hit")) +
        #This is blue:
            geom_rect(mapping = aes(xmin = index, xmax = index + 1, fill = "Statements whose coverage changed",
            ymin = ifelse(totalStatementsHitNow < totalStatementsHitPrev, totalStatementsHitNow, totalStatementsHitPrev),
            ymax = ifelse(totalStatementsHitNow < totalStatementsHitPrev, totalStatementsHitNow, totalStatementsHitPrev) + changeInSetOfLinesCovered,
            )) +
        #This is green orred
            geom_rect(mapping = aes(xmin = index, xmax = index + 1,
            ymin = ifelse(totalStatementsHitNow < totalStatementsHitPrev, 0, totalStatementsHitPrev),
            ymax = ifelse(totalStatementsHitNow < totalStatementsHitPrev, 0, totalStatementsHitNow)), show.legend = FALSE, alpha = 0.6, fill = "#BAFFA1") +
            geom_line(aes(y = coverageScaled, color = "Coverage")) +
            scale_fill_manual(values = cols, labels = c("Statements Covered", "Statements Changing Coverage")) +
            scale_color_manual(values = cols) +
            scale_y_continuous(name = "Actual Lines", sec.axis = sec_axis(~ (. - min(totalData$totalStatementsHitNow + totalData$decreasedCovg)) * 100 / yRange, name = "Overall Coverage [%]")) +
            coord_cartesian(ylim = c(min(totalData$totalStatementsHitNow + totalData$decreasedCovg)
            , max(totalData$totalStatementsHitNow + totalData$increaseCovg))
            , xlim = c(0, max(totalData$idx))) +
            scale_x_continuous(expand = c(0, 0)) +
            theme(legend.position = "bottom") +
            theme(panel.grid.major = element_blank(), panel.grid.minor = element_blank(),
            panel.background = element_blank(), legend.title = element_blank(), axis.line = element_line(colour = "black"))
        # geom_bar(aes(y=changeInSetOfLinesCovered),stat="identity",fill="red")+
        # geom_bar(aes(y=AbschangeInLinesCovered),stat="identity",fill="green")
        if (saveToDisk)
        ggsave(paste("plots/changedLines/", gsub("/", "-", proj), "-c=", max(totalData$idx), ".pdf", sep = ""), device = "pdf")
        else
        print(stupidPlot)
    }
}


#plot all jacoco
# jacocoOnly <- subset(completeData,lang=="jacoco")
# mclapply(unique(jacocoOnly$ProjectName), totalChanges, TRUE)
# mclapply(unique(completeData$ProjectName),totalChanges,TRUE)
# plot all
#lapply(unique(completeData$ProjectName),totalChanges)
# plot one
# totalChanges("lauramcastro/Erlang_Architecture_Behaviours")
# totalChanges("plasmap/geow")
# totalChanges("DSI-Ville-Noumea/po-confidential",FALSE)
