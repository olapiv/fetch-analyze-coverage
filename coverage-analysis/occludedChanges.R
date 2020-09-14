
#####################
# OCCLUDED LINES
#####################

dir.create("plots/occludedChanges", showWarnings = FALSE, recursive = TRUE)
occludedChanges <- function(proj, saveToDisk){
  
  completeData <- subset(completeData, ProjectName == proj)
  completeData <- completeData[order(completeData$idx),]
  completeData$index = nrow(completeData) - seq.int(nrow(completeData))
  completeData$missedLines <- with(completeData, totalStatementsNow - totalStatementsHitNow)
  completeData$totalUp <- with(completeData, newHitLines +
                                 deletedLinesNotTested +
                                 oldLinesNewlyTested)
  completeData$totalDown <- with(completeData, newNonHitLines +
                                   deletedLinesTested +
                                   oldLinesNoLongerTested)
  completeData$rawCoverageChange <- with(completeData, totalStatementsHitNow - totalStatementsHitPrev)
  completeData$rawSizeChange <- with(completeData, totalStatementsNow - totalStatementsPrev)
  
  occludedData <- completeData
  #occludedData <- subset(occludedData, totalStatementsNow < 5000)
  # occludedData <- subset(occludedData, ProjectName ==  "MicroTransactionsMatterToo/midi")
  # completeDatasubset(expr, cell_type == "hesc")
  
  occludedData$changeInLinesCovered <- occludedData$totalStatementsHitNow - occludedData$totalStatementsHitPrev
  
  #occludedData$increaseCovg <- occludedData$newHitLines + occludedData$oldLinesNewlyTested + occludedData$deletedLinesNotTested
  #occludedData$decreasedCovg <- (occludedData$newNonHitLines + occludedData$oldLinesNoLongerTested + occludedData$deletedLinesTested)*-1
  
  occludedData$increaseCovg <- occludedData$newHitLines + occludedData$oldLinesNewlyTested
  occludedData$decreasedCovg <- (occludedData$oldLinesNoLongerTested + occludedData$deletedLinesTested) * - 1
  
  plot <- ggplot(occludedData, aes(x = index)) +
    geom_bar(aes(y = increaseCovg, fill = "Increasing Lines"), stat = "identity", data = occludedData) +
    geom_bar(aes(y = decreasedCovg, fill = "Decreasing Lines"), stat = "identity", data = occludedData) +
    geom_bar(aes(y = changeInLinesCovered, fill = "ObservedChangeToCovg"), stat = "identity", data = occludedData, width = .5, fill = "#0000AA") +
    geom_line(aes(y = CoverageNow * 10), color = "orange", data = occludedData)# +
    # ggplot(occludedData,aes(x=index)) +
    #   geom_bar(aes(y=increaseCovg,fill="Increasing Lines"), stat="identity",data=occludedData, fill = "#009900")+
    #   geom_bar(aes(y=decreasedCovg,fill="Decreasing Lines"), stat="identity",data=occludedData,  fill = "#990000")
    #   geom_bar(aes(y=changeInLinesCovered,fill="ObservedChangeToCovg"), stat="identity",data=occludedData, width=.5,fill = "#0000AA")
  if(saveToDisk)
     ggsave(paste("plots/occludedChanges/", gsub("/", "-", proj), ".pdf", sep = ""), device = "pdf")
  else
  print(plot)
}
#lapply(unique(jacocoOnly$ProjectName),occludedChanges)
# plot all
lapply(unique(completeData$ProjectName),occludedChanges,TRUE)
# plot one
occludedChanges(("dropwizard/dropwizard"), FALSE)

