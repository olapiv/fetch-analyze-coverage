
#View(flappingCov)


#test.table <- table(flappingCov[,2:3])
#tests <- flappingCov[]

#lineCounts  <- aggregate(.~file+line,flappingCov, sum)
#
#data.frame ( table ( flappingCov$file, flappingCov$line ) )

#aggregate(x = flappingCov, 
#          by = list(flappingCov$file),sum)

# flappingCov$fileLine <-  paste(flappingCov$file, flappingCov$line, sep=':') 
#  # flappingCov$file + ":" + flappingCov$line
# 
# #aggregate(fileLine,flappingCov, sum)
# 
# faggdata <- aggregate(flappingCov, by=fileLine, 
#                     FUN=sum, na.rm=TRUE)
# 
# aggregate(x = flappingCov, 
#           by = fileLine, Fun=sum)
# 
# aggregate(x ~ file + line, data = flappingCov, FUN = length)

#flapCount <- count(flappingCov, c('line','file')) 

#View(flappingAll)
#data.frame ( table ( flappingAll$file, flappingAll$line ) )

#flappingAll <- flappingAll[]
#flappingAll 168038
flappingAll$fileLine <-  paste(flappingAll$file, flappingAll$line, sep=':') 
#flappingAll <- flappingAll[1:500,]
flipCount <- (data.frame ( table ( flappingAll$fileLine ) ))
colnames(flipCount) <- c("file:line","count")
#plot(flipCount)
#View(data.frame ( table ( flappingAll$fileLine ) ))

#merge()
fileCountAndData <- merge(flipCount, flappingAll, by.x = 'file:line', by.y = 'fileLine', all.x = TRUE)

fileCountAndData <- merge(fileCountAndData, allData[c("childSha","pid")], by.x = 'commit', by.y = 'childSha', all.x = TRUE,suffixes = c("a","b"))

# hist(flipCount$count)
#print(aggregate(fileCountAndData$count,list(completeData$lang),mean))

#IDEAS:
#flapping/num builds
#flaps to total number of changes
#flapping/total statements (or number of statements covered)
#fips/coverage transitions
#flappy vs not flappy changes

lineFlipCount <-  unique(fileCountAndData[c("file:line","count","file","pid")])
# hist(lineFlipCount$count)

#aggregate(lineFlipCount$count,list(lineFlipCount$lang),mean)

allFlipsPerFile <- aggregate(lineFlipCount$count,list(lineFlipCount$file),sum)
colnames(allFlipsPerFile) <- c("file","totalFlips")
countFlippedLinesPerFile <- as.data.frame(table(lineFlipCount$file))
colnames(countFlippedLinesPerFile) <- c("file","totalFlippedLines")

flipsPerLine <- merge(countFlippedLinesPerFile, allFlipsPerFile, by.x = 'file', by.y = 'file', all.x = TRUE)
flipsPerLine <- merge(flipsPerLine, unique(lineFlipCount[c("file","pid")]), by.x = 'file', by.y = 'file', all.x = TRUE,suffixes = c("a","b"))
flipsPerLine$flipFactor <- flipsPerLine$totalFlips/flipsPerLine$totalFlippedLines
#View(flipsPerLine)
#flipsPerProject <- aggregate(flipsPerLine$count,list(lineFlipCount$ProjectName),sum)

flipsPerProject <- aggregate(list(flipsPerLine$totalFlippedLines, flipsPerLine$totalFlips), by = list(flipsPerLine$pid), sum)
colnames(flipsPerProject) <- c("ProjectName","flippedLines","totalFlips")

flipsPerProject$flipFactor <- flipsPerProject$totalFlips/flipsPerProject$flippedLines

# hist(flipsPerProject$flipFactor)


# ggplot(flipsPerProject, aes(y=flipFactor,x=ProjectName)) +
#   geom_bar(stat='identity') +
#   coord_flip()+
#   ggsave(paste("../paper/figures/flipFactor.pdf",sep=""),
#          width = 7,units = "in",device="pdf")


#boxplot(count~ProjectName,data=fileCountAndData, main="Car Milage Data", 
#        xlab="Number of Cylinders", ylab="Miles Per Gallon")

#TODO: Change to GGplot
# boxplot(count~ProjectName,data=fileCountAndData, main="flips per line dist", horizontal= TRUE)
fileCountAndData <- subset(fileCountAndData,! is.na(pid))

plot <- ggplot(fileCountAndData, aes(x = pid, y = count)) +
  geom_boxplot()+
  coord_flip() +
  scale_y_continuous(expand = c(0, 0)) +
  # ylab("Number of times transitioning coverage")+
  
  geom_boxplot(fill=lightGreen, color=darkGreen) +
  basicFormat+
  ggsave(paste("../paper/figures/flipFactor.pdf",sep=""),
         width=7,units="in",device="pdf")
print(plot)


# quantile(allCur$totalPatchSize) 

writeLines(capture.output(
  stargazer(quantile(allCur$totalPatchSize) ,summary=FALSE,digits=3
            ,title = "Quartile distribution of total changes in coverage to lines with at least one change to coverage",
            label="TABLE:distributionFlips"
  )), "../paper/AllFlipsDist.tex")