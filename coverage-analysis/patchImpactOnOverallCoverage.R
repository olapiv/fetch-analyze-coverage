require(parallel)
library(dplyr)

dir.create("plots/patchImpact", showWarnings = FALSE, recursive = TRUE)

fills <- c( "C- empty"="#CF5D3F",
            "C- notempty"="#A03A34",
            "A+ empty" = "#95B661", 
           "A+ notempty" = "#568C54", 
           "B~ notempty" = "#F2CC60",
           "B~ empty" = "#F2CC60")
fillLabels <-  c("C- empty"="Decrease",
                 "C- notempty"="Decrease",
                 "A+ empty" = "Increase", 
                 "A+ notempty" = "Increase", 
                 "B~ empty" = "No impact",
                 "B~ notempty" = "No impact")

# patchImpact <- function(filteredData, saveToDisk){
filteredData <- allData
  
  filteredData <- filteredData[! duplicated(filteredData[, c('parentSha')]),]
  
  filteredData <- filteredData[order(filteredData$idx),]
  
  filteredData$index = seq.int(nrow(filteredData))
  filteredData$rawCoverageChange <- with(filteredData, totalStatementsHitNow - totalStatementsHitPrev)
  #completeData$changeToOld <- with(completeData,oldLinesNewlyTested-oldLinesNoLongerTested)
  filteredData$changeToOld <- with(filteredData,
                                   ifelse(oldLinesNewlyTested==oldLinesNoLongerTested,
                                          "B~",ifelse(oldLinesNewlyTested>oldLinesNoLongerTested,
                                                      "A+","C-")))
  filteredData$diffSize <- with(filteredData,newHitLines + newNonHitLines+
                                  deletedLinesNotTested +
                                  deletedLinesTested)
  filteredData$diffBucket <- cut(filteredData$newLinesSrc+filteredData$delLinesSrc+filteredData$newLinesTest+filteredData$delLinesTes,c(-1,0,1000000),labels=c("empty","notempty"))
  
  tab <- table(filteredData$changeToOld,filteredData$diffBucket,filteredData$pid)
  tab<-melt(tab,c("Change","PatchSize", "Project"))
  tab <- group_by(tab,Project)%>% mutate(percent = (100*value/sum(value)), tot = sum(value))
  tab <- as.data.frame(tab)
  tab$cat <- with(tab,paste(Change,PatchSize))
  tab$cat = factor(tab$cat, level=c("A+ notempty","A+ empty","B~ empty","B~ notempty","C- empty","C- notempty"))
  tab$pidNumericInverse<-49-as.numeric(substr(tab$Project,2,5))
  tab$pidNumeric<-as.numeric(substr(tab$Project,2,5))
  tab <- tab[order(tab$pidNumericInverse),]
  
  localFormat <- theme(panel.grid.major = element_blank(), panel.grid.minor = element_blank(),
                       plot.title = element_text(hjust = 0.5),
                       axis.title.y=element_blank(),
                       axis.title.x=element_blank(),
                       panel.background = element_blank(), axis.line = element_line(colour = "black"), 
                       legend.key.size=unit(10,"pt"),
                       legend.position= c(0.5,-0.08),
                       legend.background = element_rect(fill="transparent"),
                       aspect.ratio=0.5,text=element_text(size=8,  family="Times"))
  
  
  # tab<-subset(tab,tot>)
  # print(tab)
    stupidPlot <- ggplot(tab, aes(x=Project)) +
      # ggtitle("Impact of each commit on coverage of existing code") +
      geom_bar(aes(y=percent,fill=cat), stat="identity", data=tab) +
      scale_fill_manual(values = fills, labels = fillLabels, 
                        name="Patches with changes to code files:\nPatches with no changes to code files:") +
      guides(fill=guide_legend(ncol=3,title.position="left"))+
      scale_y_continuous(expand = c(0, 0)) +
      # scale_x_continuous(breaks=unique(filteredData$pidNumeric),labels=pid,limits = c(1,NA))+
      ylab("Percentage of Commits")+
      # xlab("Project Name")+
      localFormat + 
       coord_flip()
    # if (saveToDisk)
      ggsave(paste("../paper/figures/patchImpact.pdf", sep = ""), width=7,units="in",device = "pdf")
    # else
      print(stupidPlot)
      print("Warning: THE LABELS IN PATCH IMPACT.PDF ARE PROBABLY NOT DISPLAYING CORRECTLY IN THE LEGEND, THEY NEED TO BE MANUALLY SHUFFLED (decrease with lighter red should be on bottom)")
  # }
# }


#plot all jacoco
#jacocoOnly <- subset(completeData,lang=="jacoco")
#dw <- subset(completeData,ProjectName=="dropwizard/dropwizard")

# mclapply(unique(jacocoOnly$ProjectName), patchImpact, TRUE)
# mclapply(unique(completeData$ProjectName),totalChanges,TRUE)
# plot all
#lapply(unique(completeData$ProjectName),totalChanges)
# plot one
# totalChanges("lauramcastro/Erlang_Architecture_Behaviours")
#totalChanges("plasmap/geow")
# totalChanges("DSI-Ville-Noumea/po-confidential",FALSE)
# patchImpact(jacocoOnly,FALSE)
# patchImpact(allData,FALSE)