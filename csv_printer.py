import json
import csv
import pandas


def print_csv():
    colString = "repo,childSha,parentSha,childBranch,timestamp,modifiedLinesNewlyHit,modifiedLinesNoLongerHit,modifiedLinesStillHit,newLinesHit,newLinesNotHit,newFileLinesHit,newFileLinesNotHit,deletedLinesHit,deletedLinesNotHit,deletedFileLinesHit,deletedFileLinesNotHit,oldLinesNewlyHit,oldLinesNoLongerHit,nStatementsHitInBoth,nStatementsHitInEither,totalStatementsHitNow,totalStatementsNow,totalStatementsHitPrev,totalStatementsPrev,modFilesSrc,delFilesSrc,insFilesSrc,modFilesTest,delFilesTest,insFilesTest,insLinesSrc,delLinesSrc,insLinesTest,delLinesTest,insLinesAllFiles,delLinesAllFiles"
    colnames = colString.split(",")
    data = pandas.read_csv('/Users/vincent/Desktop/fetch-analyze-coverage/coveralls_importer/temp/outFile.csv', names=colnames)
    data = data[['repo', 'oldLinesNewlyHit', 'oldLinesNoLongerHit', 'modifiedLinesNewlyHit', 'modifiedLinesNoLongerHit']]

    pandas.set_option('display.max_rows', data.shape[0]+1)
    print(data)


if __name__ == '__main__':
    print_csv()
