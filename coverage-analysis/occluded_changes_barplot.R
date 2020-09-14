library(dplyr)
library(ggplot2)




occludedChanges<- allData
occludedChanges$isNoChange <- ifelse(abs(occludedChanges$ActualChangeToCoverage)<0.0001,"Y","N")
occludedChanges <- subset(occludedChanges,isNoChange=="Y")

occludedChanges$changeInCoveredStatements <- occludedChanges$nStatementsInEither - occludedChanges$nStatementsInBoth

occludedChanges$bucket <- cut(occludedChanges$changeInCoveredStatements, breaks = c(0, 1, 10, 100, 1000, 10000000), labels = c("0","1-10", "11-100","101-1,000","1,001+"),include.lowest=1)

tab <- t(table(occludedChanges$bucket, occludedChanges$pid))
tab<-melt(tab,c("Project","Impact"))
tab <- group_by(tab,Project)%>% mutate(percent = (100*value/sum(value)), tot = sum(value))
tab <- as.data.frame(tab)

fills <- c( "0"="#428f4d",
            "1-10"="#f8cc47",
            "11-100" = "#e9923e", 
            "101-1,000" = "#de5434", 
            "1,001+" = "#ad302e")

labels = c("0","1-10", "11-100","101-1,000","1,001-10,000",">10,000")
tab$Impact=factor(tab$Impact, level=rev(c("0","1-10", "11-100","101-1,000","1,001+")))

localFormat <- theme(panel.grid.major = element_blank(), panel.grid.minor = element_blank(),
                     plot.title = element_text(hjust = 0.5),
                     axis.title.y=element_blank(),
                     axis.title.x=element_blank(),
                     panel.background = element_blank(), axis.line = element_line(colour = "black"), 
                     legend.key.size=unit(10,"pt"),
                     legend.position= c(0.5,-0.07),
                     legend.title=element_blank(),
                     legend.background = element_rect(fill="transparent"),
                     aspect.ratio=0.5,text=element_text(size=8,  family="Times"))


plot <-ggplot(tab,aes(x = Project)) + 
  geom_bar(aes(fill = Impact,y = percent),stat = "identity")+ 
  scale_fill_manual(values = fills, guide=guide_legend(nrow=1,reverse=T)) +
  coord_flip()+
  scale_y_continuous(expand = c(0, 0)) +
  guides(fill=guide_legend(ncol=5,title.position="left",reverse=T))+
  theme(legend.position = "bottom") +
  # scale_fill_manual(values = fills) +
  localFormat
  
ggsave(paste("../paper/figures/occludedImpact.pdf", sep = ""), width=7,units="in",device = "pdf")

 print(plot)

# tab<-melt(tab,c("Change","PatchSize", "Project"))
# tab <- group_by(tab,Project)%>% mutate(percent = (100*value/sum(value)), tot = sum(value))
# tab <- as.data.frame(tab)