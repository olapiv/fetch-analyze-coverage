#Corrolate patch coverage with overall change to coverage

allCur <- allData
allCur$PercentageOfDiffCovered <- with(allCur,(newHitLines+newFileHitLines)/(newHitLines+newNonHitLines+newFileNonHitLines+newFileNonHitLines))
clean = subset(allCur, 
               !is.na(PercentageOfDiffCovered) &
                 is.finite(PercentageOfDiffCovered) &
                 !is.nan(PercentageOfDiffCovered)
)

#hist(clean$ActualChangeToCoverage)

#cor.test(x = clean$PercentageOfDiffCovered, y = clean$ActualChangeToCoverage, use="complete.obs")

#nonPatch hit = new hit - patch hit
#non-patch total = new total - patch total

#change in total = non-patch hit/non-patch total
clean$patchHit <- clean$newFileHitLines + clean$newHitLines
clean$patchTotal <- clean$newFileNonHitLines + clean$newNonHitLines
clean$nonPatchHit <- clean$totalStatementsHitNow-clean$patchHit
clean$nonPatchTotal <- clean$totalStatementsNow-clean$patchTotal

clean$changeToNonPatchCoverage <- (clean$nonPatchHit - clean$totalStatementsHitPrev)/(clean$nonPatchTotal - clean$totalStatementsPrev)
clean = subset(clean, 
               !is.na(changeToNonPatchCoverage) &
                 is.finite(changeToNonPatchCoverage) &
                 !is.nan(changeToNonPatchCoverage)
)

cor.test(x = clean$PercentageOfDiffCovered, y = clean$changeToNonPatchCoverage, use="complete.obs",method="kendall")