


subTable <- jacocoOnly[,c("ProjectName","totalStatementsHitNow","totalStatementsHitPrev","oldLinesNewlyTested","oldLinesNoLongerTested")]

#subTable <- merge(subTable, unique(jacocoOnly$ProjectName), by.x = 'ProjectName', by.y = 'ProjectName', all.y = TRUE)



subTable$observedChange <- subTable$totalStatementsHitNow -subTable$totalStatementsHitPrev
subTable$numberOfStatementsChanged <- subTable$oldLinesNewlyTested + subTable$oldLinesNoLongerTested
subTable$absObservedChanged <- abs(subTable$observedChange)
subTable <- subTable[subTable$numberOfStatementsChanged>0,]

#View(aggregate(subTable$ProjectName,list(subTable$numberOfStatementsChanged,subTable$absObservedChanged),mean))

aggTable <- aggregate(subTable,list(subTable$ProjectName),mean)

aggTable$Ratio <- aggTable$numberOfStatementsChanged/aggTable$absObservedChanged

View(aggTable)

barplot(aggTable$Ratio)