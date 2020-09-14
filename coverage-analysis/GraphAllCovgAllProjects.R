#View(allData)

minIdx <- aggregate(idx ~ ProjectName, allData, function(x) min(x))
maxIdx <- aggregate(idx ~ ProjectName, allData, function(x) max(x))

addDataWithMinMax <- merge(allData, minIdx, by.x = 'ProjectName', by.y = 'ProjectName', all.x = TRUE,suffixes = c("",".min"))
addDataWithMinMax <- merge(addDataWithMinMax, maxIdx, by.x = 'ProjectName', by.y = 'ProjectName', all.x = TRUE,suffixes = c("",".max"))

addDataWithMinMax$normalizedIDx <- (addDataWithMinMax$idx - addDataWithMinMax$idx.min)/(addDataWithMinMax$idx.max-addDataWithMinMax$idx.min)

ggplot(addDataWithMinMax, aes(normalizedIDx, CoverageNow)) +
  geom_line(aes(group = ProjectName, color = ProjectName)) + 
  theme(legend.position="none") +
  ggsave(paste("../paper/figures/allcovovertime.pdf",sep=""),
         width = 7, height = 3,   units = "in",device="pdf")

#View(addDataWithMinMax)