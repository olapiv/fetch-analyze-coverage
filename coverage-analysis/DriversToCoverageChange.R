
#####################
# Drivers To Cov Change
#####################
dir.create("plots/DriversToChange", showWarnings = FALSE, recursive = TRUE)
driverToCovChange <- allData
driverToCovChange <- driverToCovChange[order(driverToCovChange$idx),]
#Changes that increase coverage
driverToCovChange$existingLinesNewCoverage <- driverToCovChange$oldLinesNewlyTested
driverToCovChange$testedNewLines <- driverToCovChange$newHitLines + driverToCovChange$newFileHitLines
driverToCovChange$deletedUntestedLines <- driverToCovChange$deletedFileLinesNotTested + driverToCovChange$deletedLinesNotTested
#Changes that decrease coverage
driverToCovChange$existingLinesNoLongerCovered <- driverToCovChange$oldLinesNoLongerTested
driverToCovChange$nonTestedNewLines <- driverToCovChange$newFileNonHitLines+ driverToCovChange$newNonHitLines
driverToCovChange$deletedTestedLines <- driverToCovChange$deletedFileLinesTested + driverToCovChange$deletedLinesTested

driverToCovChange$sumOfChanges <- 
  driverToCovChange$existingLinesNewCoverage + 
  driverToCovChange$testedNewLines +
  driverToCovChange$deletedUntestedLines +
  driverToCovChange$existingLinesNoLongerCovered +
  driverToCovChange$nonTestedNewLines +
  driverToCovChange$deletedTestedLines

driverToCovChange$existingLinesNewCoveragePercent <- driverToCovChange$existingLinesNewCoverage/driverToCovChange$sumOfChanges*100
driverToCovChange$testedNewLinesPercent <-driverToCovChange$testedNewLines/driverToCovChange$sumOfChanges*100
driverToCovChange$deletedUntestedLinesPercent <-   driverToCovChange$deletedUntestedLines/driverToCovChange$sumOfChanges*100
driverToCovChange$existingLinesNoLongerCoveredPercent <-   driverToCovChange$existingLinesNoLongerCovered/driverToCovChange$sumOfChanges*100
driverToCovChange$nonTestedNewLinesPercent <-   driverToCovChange$nonTestedNewLines/driverToCovChange$sumOfChanges*100
driverToCovChange$deletedTestedLinesPercent <-   driverToCovChange$deletedTestedLines/driverToCovChange$sumOfChanges*100
  

driverToCovChange <- driverToCovChange[driverToCovChange$sumOfChanges>0,]

changeTable <- aggregate(
  list(driverToCovChange$existingLinesNewCoveragePercent,
       driverToCovChange$testedNewLinesPercent,
       driverToCovChange$deletedUntestedLinesPercent,
       driverToCovChange$existingLinesNoLongerCoveredPercent,
       driverToCovChange$nonTestedNewLinesPercent,
       driverToCovChange$deletedTestedLinesPercent),
  list(driverToCovChange$pid),mean)

colnames(changeTable) <- c("ProjectName",
                           "Added coverage to existing lines",
                           "Added new lines that are covered",
                           "Deleted untested lines",
                           "Coverage lost on existing lines",
                           "Added new lines that are not covered",
                           "Deleted tested lines")
head(changeTable, n=10)

dfm <- melt(changeTable[,c("ProjectName",
                           "Deleted tested lines",
                           "Added new lines that are not covered",
                           "Coverage lost on existing lines",
                           "Added coverage to existing lines",
                           "Added new lines that are covered",
                           "Deleted untested lines")],id.vars = 1)

lightRed <- "#EC564D"
medRed <- "#A03A34"
darkRed <- "#6D2823"

#lightGreen <- "#95B661"
lightGreen <- "#95B661"
medGreen <- "#568C54"
darkGreen <- "#375935"

lightYellow <- "#F2CC60"

fills <- c( "Added coverage to existing lines"=lightGreen,
            "Added new lines that are covered"=medGreen,
            "Deleted untested lines" = darkGreen, 
            "Coverage lost on existing lines" = lightRed,
            "Added new lines that are not covered" = medRed,
            "Deleted tested lines" =darkRed)

fillLabels<- c( "Added coverage to existing lines"="#CF5D3F",
"Added new lines that are covered"="#A03A34",
"Deleted untested lines" = "#95B661", 
"A+ notempty" = "#568C54", 
"Coverage lost on existing lines" = "#F2CC60",
"Added new lines that are not covered" = "#F2CC60",
"Deleted tested lines" = "#F2CC60")

colnames(dfm) <- c("ProjectName","Impact","value")

localFormat <- theme(panel.grid.major = element_blank(), panel.grid.minor = element_blank(),
                     plot.title = element_text(hjust = 0.5),
                     axis.title.y=element_blank(),
                     axis.title.x=element_blank(),
                     panel.background = element_blank(), axis.line = element_line(colour = "black"), 
                     legend.key.size=unit(10,"pt"),
                     legend.position= c(0.5,-0.08),
                     legend.title=element_blank(),
                     legend.background = element_rect(fill="transparent"),
                     aspect.ratio=0.5,text=element_text(size=8,  family="Times"))

plot <-ggplot(dfm,aes(x = ProjectName,y = value)) + 
  geom_bar(aes(fill = Impact),stat = "identity")+ 
  coord_flip()+
  scale_y_continuous(expand = c(0, 0)) +
  theme(legend.position = "bottom") +
  scale_fill_manual(values = fills) +
  guides(fill=guide_legend(ncol=3,title.position="left"))+
  
  ylab("Percent of total changes accounted for")+
  localFormat+
   ggsave(paste("../paper/figures/driverToChange.pdf",sep=""),width=7,units="in",device="pdf")

print(plot)
# writeLines(capture.output(
#   stargazer(changeTable,summary=FALSE,digits=1
#   ,dep.var.caption = " "
#   )), "../paper/changeTable.tex")