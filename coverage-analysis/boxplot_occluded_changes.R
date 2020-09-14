#boxplot of occludedChanges
occludedChanges<- allData
occludedChanges$changesInCoveredStatements <- abs(occludedChanges$totalStatementsHitNow-occludedChanges$totalStatementsHitPrev)

#Changes that increase coverage
occludedChanges$existingLinesNewCoverage <- occludedChanges$oldLinesNewlyTested
occludedChanges$testedNewLines <- occludedChanges$newHitLines + occludedChanges$newFileHitLines
occludedChanges$deletedUntestedLines <- occludedChanges$deletedFileLinesNotTested + occludedChanges$deletedLinesNotTested
#Changes that decrease coverage
occludedChanges$existingLinesNoLongerCovered <- occludedChanges$oldLinesNoLongerTested
occludedChanges$nonTestedNewLines <- occludedChanges$newFileNonHitLines+ occludedChanges$newNonHitLines
occludedChanges$deletedTestedLines <- occludedChanges$deletedFileLinesTested + occludedChanges$deletedLinesTested


occludedChanges$sumOfChanges <- 
  occludedChanges$existingLinesNewCoverage + 
  occludedChanges$testedNewLines +
  occludedChanges$deletedUntestedLines +
  occludedChanges$existingLinesNoLongerCovered +
  occludedChanges$nonTestedNewLines +
  occludedChanges$deletedTestedLines

# hist(abs(occludedChanges$changesInCoveredStatements-occludedChanges$sumOfChanges))

occludedChanges$occludedChanges <- occludedChanges$sumOfChanges-occludedChanges$changesInCoveredStatements

occludedChanges <- occludedChanges[occludedChanges$occludedChanges<5000,]
occludedChanges <- occludedChanges[occludedChanges$occludedChanges>-1,]
occludedChanges <- occludedChanges[order(occludedChanges$pidNumeric),]


buildCounts <- aggregate(x = occludedChanges, by = list(unique.values = occludedChanges$ProjectName), FUN = length)
buildCounts <- buildCounts[,1:2]
colnames(buildCounts) <- c("ProjectName","BuildCount")

# occludedChangesSum <- aggregate(x = occludedChanges$occludedChanges, by = list(unique.values = occludedChanges$ProjectName), FUN = length)
# colnames(buildCounts) <- c("ProjectName","BuildCount")

allOccluded <- merge(occludedChanges,buildCounts,by.x="ProjectName",by.y="ProjectName", all.x=TRUE)

allPositiveOccluded <- allOccluded[allOccluded$occludedChanges>0,]
allPositiveOccluded$normalizedOccluded <- allPositiveOccluded$occludedChanges/allPositiveOccluded$BuildCount

plot <- ggplot(allPositiveOccluded, aes(x = pid, y = normalizedOccluded)) +
  geom_boxplot()+
  coord_flip() +
  scale_y_continuous(expand = c(0, 0)) +
  ylab("Distribution of number of occluded changes per commit with at least one change per project")+
  geom_boxplot(fill=lightGreen, color=darkGreen) +
  basicFormat
  ggsave(paste("../paper/figures/occludedChanges.pdf",sep=""),
         width = 7,units = "in",device="pdf")
print(plot)