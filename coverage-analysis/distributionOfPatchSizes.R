# Distribution of patch sizes

# d <- density(allCur$totalPatchSize) # returns the density data 
# plot(d) # plots the results
# 
# ggplot(allCur, aes(totalPatchSize)) +
#   geom_boxplot(
#     aes(ymin = y0, lower = y25, middle = y50, upper = y75, ymax = y100),
#     stat = "identity"
#   )

allCur$totalPatchSize <- with(allCur,newHitLines+newNonHitLines+deletedLinesTested+deletedLinesNotTested)
boxplot(allCur$totalPatchSize)

quantile(allCur$totalPatchSize) 

writeLines(capture.output(
  stargazer(quantile(allCur$totalPatchSize) ,summary=FALSE,digits=3
            ,title = "Distribution number of statement changes across patches",
            label="TABLE:distributionOfPatchSizes"
  )), "../paper/patchSizeDist.tex")