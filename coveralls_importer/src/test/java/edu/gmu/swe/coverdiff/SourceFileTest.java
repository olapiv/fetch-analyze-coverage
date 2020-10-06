package edu.gmu.swe.coverdiff;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;


public class SourceFileTest {

    CoverallsImporter.SourceFile curr = new CoverallsImporter.SourceFile();
    CoverallsImporter.SourceFile prev = new CoverallsImporter.SourceFile();

    @Before
    public void setUp() {
    }

    @Test
    public void testCountPrevTotalLines() {
        prev.coverage = new ArrayList<>();
        prev.coverage.add(0,null);
        prev.coverage.add(1,0);
        prev.coverage.add(2,0);
        prev.coverage.add(3,1);
        prev.coverage.add(4,0);
        prev.coverage.add(5,0);
        prev.coverage.add(6,0);
        curr.coverage = new ArrayList<>();
        curr.coverage.add(0,null);
        curr.coverage.add(1,0);
        curr.coverage.add(2,1);
        curr.coverage.add(3,0);
        curr.coverage.add(4,0);
        curr.coverage.add(5,0);
        curr.deletedLines = new HashSet<>();
        curr.deletedLines.add(2);
        curr.lineMapping = new HashMap<>();
        curr.lineMapping.put(0,0);
        curr.lineMapping.put(1,1);
        curr.lineMapping.put(2,3);
        curr.lineMapping.put(3,4);
        curr.lineMapping.put(4,5);
        curr.lineMapping.put(5,6 );

        CoverallsImporter.DiffResult diff = curr.diff(prev);
        assertEquals(6,diff.totalStatementsPrev);
    }

    @Test
    public void testCountPrevTotalLines2() {
        prev.coverage = new ArrayList<>();
        prev.coverage.add(0,0);
        prev.coverage.add(1,0);
        prev.coverage.add(2,0);
        curr.coverage = new ArrayList<>();
        curr.coverage.add(0,0);
        curr.coverage.add(1,0);

        curr.deletedLines = new HashSet<>();
        curr.deletedLines.add(0);
        curr.lineMapping = new HashMap<>();
        curr.lineMapping.put(0,1);
        curr.lineMapping.put(1,2);

        CoverallsImporter.DiffResult diff = curr.diff(prev);
        assertEquals(3,diff.totalStatementsPrev);
        assertEquals(2,diff.totalStatementsNow);
    }

    @Test
    public void testCountPrev1Line() {
        prev.coverage = new ArrayList<>();
        prev.coverage.add(0,1);
        prev.coverage.add(1,1);
        curr.coverage = new ArrayList<>();
        curr.coverage.add(0,0);
        curr.deletedLines = new HashSet<>();
        curr.deletedLines.add(1);
        curr.lineMapping = new HashMap<>();
        curr.lineMapping.put(1,0);


        CoverallsImporter.DiffResult diff = curr.diff(prev);
        assertEquals(1,diff.totalStatementsPrev);
    }


    @Test
    public void diff_no_lines() {
        curr.coverage = Arrays.asList();
        prev.coverage = Arrays.asList();

        CoverallsImporter.DiffResult diff = curr.diff(prev);
        // assertEquals(0,diff.modifiedLinesNewlyHit);
        // assertEquals(0,diff.modifiedLinesNoLongerHit);
        // assertEquals(0,diff.modifiedLinesStillHit);
        assertEquals(0,diff.newLinesHit);
        assertEquals(0,diff.newLinesNotHit);
        assertEquals(0,diff.deletedLinesHit);
        assertEquals(0,diff.deletedLinesNotHit);
        assertEquals(0,diff.oldLinesNewlyHit);
        assertEquals(0,diff.oldLinesNoLongerHit);
        assertEquals(0,diff.totalStatementsHitInBoth);
        assertEquals(0,diff.totalStatementsHitInEither);
        assertEquals(0,diff.totalStatementsHitNow);
        assertEquals(0,diff.totalStatementsNow);
        assertEquals(0,diff.totalStatementsHitPrev);
        assertEquals(0,diff.totalStatementsPrev);
    }

    @Test
    public void diff_curr_one_line() {
        curr.coverage = Arrays.asList(1);
        prev.coverage = Arrays.asList();

        CoverallsImporter.DiffResult diff = curr.diff(prev);
        // assertEquals(0,diff.modifiedLinesNewlyHit);
        // assertEquals(0,diff.modifiedLinesNoLongerHit);
        // assertEquals(0,diff.modifiedLinesStillHit);
        assertEquals(0,diff.newLinesHit);
        assertEquals(0,diff.newLinesNotHit);
        assertEquals(0,diff.deletedLinesHit);
        assertEquals(0,diff.deletedLinesNotHit);
        assertEquals(1,diff.oldLinesNewlyHit);
        assertEquals(0,diff.oldLinesNoLongerHit);
        assertEquals(0,diff.totalStatementsHitInBoth);
        assertEquals(1,diff.totalStatementsHitInEither);
        assertEquals(1,diff.totalStatementsHitNow);
        assertEquals(1,diff.totalStatementsNow);
        assertEquals(0,diff.totalStatementsHitPrev);
        assertEquals(0,diff.totalStatementsPrev);
    }

//    @Test
//    public void diff_prev_one_line() {
//        curr.coverage = Arrays.asList(0);
//        prev.coverage = Arrays.asList(1);
//
//        CoverallsImporter.DiffResult diff = curr.diff(prev);
//        assertEquals(0,diff.modifiedLinesNewlyHit);
//        assertEquals(0,diff.modifiedLinesNoLongerHit);
//        assertEquals(0,diff.modifiedLinesStillHit);
//        assertEquals(0,diff.newLinesHit);
//        assertEquals(0,diff.newLinesNotHit);
//        assertEquals(0,diff.deletedLinesHit);
//        assertEquals(0,diff.deletedLinesNotHit);
//        assertEquals(0,diff.oldLinesNewlyHit);
//        assertEquals(1,diff.oldLinesNoLongerHit);
//        assertEquals(0,diff.nStatementsHitInBoth);
//        assertEquals(1,diff.nStatementsHitInEither);
//        assertEquals(0,diff.totalStatementsHitNow);
//        assertEquals(1,diff.totalStatementsNow);
//        assertEquals(1,diff.totalStatementsHitPrev);
//        assertEquals(1,diff.totalStatementsPrev);
//    }

    @Test
    public void diff_same_line_hit() {
        prev.coverage = new ArrayList<>();
        prev.coverage.add(0,null);
        prev.coverage.add(1,1);
        curr.coverage = new ArrayList<>();
        curr.coverage.add(0,null);
        curr.coverage.add(1,1);
        curr.lineMapping = new HashMap<>();
        curr.lineMapping.put(1,1);


        CoverallsImporter.DiffResult diff = curr.diff(prev);
        // assertEquals(0,diff.modifiedLinesNewlyHit);
        // assertEquals(0,diff.modifiedLinesNoLongerHit);
        // assertEquals(0,diff.modifiedLinesStillHit);
        assertEquals(0,diff.newLinesHit);
        assertEquals(0,diff.newLinesNotHit);
        assertEquals(0,diff.deletedLinesHit);
        assertEquals(0,diff.deletedLinesNotHit);
        assertEquals(0,diff.oldLinesNewlyHit);
        assertEquals(0,diff.oldLinesNoLongerHit);
        assertEquals(1,diff.totalStatementsHitInBoth);
        assertEquals(1,diff.totalStatementsHitInEither);
        assertEquals(1,diff.totalStatementsHitNow);
        assertEquals(1,diff.totalStatementsNow);
        assertEquals(1,diff.totalStatementsHitPrev);
        assertEquals(1,diff.totalStatementsPrev);
    }


    @Test
    public void diff_diff_line_hit() {
        curr.coverage = new ArrayList<>();
        curr.coverage.add(0,null);
        curr.coverage.add(1,0);
        curr.coverage.add(2,1);
        prev.coverage = new ArrayList<>();
        prev.coverage.add(0,null);
        prev.coverage.add(1,1);
        prev.coverage.add(2,0);
        curr.lineMapping = new HashMap<>();
        curr.lineMapping.put(1,1);
        curr.lineMapping.put(2,2);

        CoverallsImporter.DiffResult diff = curr.diff(prev);
        // assertEquals(0,diff.modifiedLinesNewlyHit);
        // assertEquals(0,diff.modifiedLinesNoLongerHit);
        // assertEquals(0,diff.modifiedLinesStillHit);
        assertEquals(0,diff.newLinesHit);
        assertEquals(0,diff.newLinesNotHit);
        assertEquals(0,diff.deletedLinesHit);
        assertEquals(0,diff.deletedLinesNotHit);
        assertEquals(1,diff.oldLinesNewlyHit);
        assertEquals(1,diff.oldLinesNoLongerHit);
        assertEquals(0,diff.totalStatementsHitInBoth);
        assertEquals(2,diff.totalStatementsHitInEither);
        assertEquals(1,diff.totalStatementsHitNow);
        assertEquals(2,diff.totalStatementsNow);
        assertEquals(1,diff.totalStatementsHitPrev);
        assertEquals(2,diff.totalStatementsPrev);
    }

    @Test
    public void diff_oneLinesNewlyHit() {
        prev.coverage = new ArrayList<>();
        prev.coverage.add(0,null);
        prev.coverage.add(1,0);
        curr.coverage = new ArrayList<>();
        curr.coverage.add(0,null);
        curr.coverage.add(1,1);
        curr.lineMapping = new HashMap<>();
        curr.lineMapping.put(1,1);



        CoverallsImporter.DiffResult diff = curr.diff(prev);
        // assertEquals(0,diff.modifiedLinesNewlyHit);
        // assertEquals(0,diff.modifiedLinesNoLongerHit);
        // assertEquals(0,diff.modifiedLinesStillHit);
        assertEquals(0,diff.newLinesHit);
        assertEquals(0,diff.newLinesNotHit);
        assertEquals(0,diff.deletedLinesHit);
        assertEquals(0,diff.deletedLinesNotHit);
        assertEquals(1,diff.oldLinesNewlyHit);
        assertEquals(0,diff.oldLinesNoLongerHit);
        assertEquals(0,diff.totalStatementsHitInBoth);
        assertEquals(1,diff.totalStatementsHitInEither);
        assertEquals(1,diff.totalStatementsHitNow);
        assertEquals(1,diff.totalStatementsNow);
        assertEquals(0,diff.totalStatementsHitPrev);
        assertEquals(1,diff.totalStatementsPrev);
    }


    @Test
    public void diff_oneLinesNoLongerHit() {
        prev.coverage = new ArrayList<>();
        prev.coverage.add(0,null);
        prev.coverage.add(1,1);
        curr.coverage = new ArrayList<>();
        curr.coverage.add(0,null);
        curr.coverage.add(1,0);
        curr.lineMapping = new HashMap<>();
        curr.lineMapping.put(1,1);

        CoverallsImporter.DiffResult diff = curr.diff(prev);
        // assertEquals(0,diff.modifiedLinesNewlyHit);
        // assertEquals(0,diff.modifiedLinesNoLongerHit);
        // assertEquals(0,diff.modifiedLinesStillHit);
        assertEquals(0,diff.newLinesHit);
        assertEquals(0,diff.newLinesNotHit);
        assertEquals(0,diff.deletedLinesHit);
        assertEquals(0,diff.deletedLinesNotHit);
        assertEquals(0,diff.oldLinesNewlyHit);
        assertEquals(1,diff.oldLinesNoLongerHit);
        assertEquals(0,diff.totalStatementsHitInBoth);
        assertEquals(1,diff.totalStatementsHitInEither);
        assertEquals(0,diff.totalStatementsHitNow);
        assertEquals(1,diff.totalStatementsNow);
        assertEquals(1,diff.totalStatementsHitPrev);
        assertEquals(1,diff.totalStatementsPrev);
    }


//    @Test
//    public void diff_withOffLineupTests() {
//        prev.coverage = new ArrayList<>();
//        prev.coverage.add(0,null);
//        prev.coverage.add(1,1);
//        prev.coverage.add(2,0);
//        curr.coverage = new ArrayList<>();
//        curr.coverage.add(0,null);
//        curr.coverage.add(1,0);
//        curr.coverage.add(2,1);
//        curr.lineMapping = new HashMap<Integer, Integer>();
//        curr.lineMapping.put(1,2);
//
//
//
//        CoverallsImporter.DiffResult diff = curr.diff(prev);
//        assertEquals(0,diff.modifiedLinesNewlyHit);
//        assertEquals(0,diff.modifiedLinesNoLongerHit);
//        assertEquals(0,diff.modifiedLinesStillHit);
//        assertEquals(0,diff.newLinesHit);
//        assertEquals(0,diff.newLinesNotHit);
//        assertEquals(0,diff.deletedLinesHit);
//        assertEquals(0,diff.deletedLinesNotHit);
//        assertEquals(0,diff.oldLinesNewlyHit);
//        assertEquals(0,diff.oldLinesNoLongerHit);
//        assertEquals(1,diff.nStatementsHitInBoth);
//        assertEquals(1,diff.nStatementsHitInEither);
//        assertEquals(0,diff.totalStatementsHitNow);
//        assertEquals(1,diff.totalStatementsNow);
//        assertEquals(2,diff.totalStatementsHitPrev);
//        assertEquals(1,diff.totalStatementsPrev);
//    }

/*
    @Test
    public void testmodifiedLinesNewlyHit() {
        prev.coverage = new ArrayList<>();
        prev.coverage.add(0,null);
        prev.coverage.add(1,0);
        curr.coverage = new ArrayList<>();
        curr.coverage.add(0,null);
        curr.coverage.add(1,1);
        curr.modifiedLines = new HashSet<>();
        curr.modifiedLines.add(1);

        CoverallsImporter.DiffResult diff = curr.diff(prev);
        assertEquals(1,diff.modifiedLinesNewlyHit);
        assertEquals(0,diff.modifiedLinesNoLongerHit);
        assertEquals(0,diff.modifiedLinesStillHit);
    }

    @Test
    public void testMultipleModifiedLinesNewlyHit() {
        prev.coverage = new ArrayList<>();
        prev.coverage.add(0,null);
        prev.coverage.add(1,0);
        prev.coverage.add(2,0);
        curr.coverage = new ArrayList<>();
        curr.coverage.add(0,null);
        curr.coverage.add(1,1);
        curr.coverage.add(2,1);
        curr.modifiedLines = new HashSet<>();
        curr.modifiedLines.add(1);
        curr.modifiedLines.add(2);

        CoverallsImporter.DiffResult diff = curr.diff(prev);
        assertEquals(2,diff.modifiedLinesNewlyHit);
        assertEquals(0,diff.modifiedLinesNoLongerHit);
        assertEquals(0,diff.modifiedLinesStillHit);
    }


    @Test
    public void testModifiedLinesNoLongerHit() {
        prev.coverage = new ArrayList<>();
        prev.coverage.add(0,null);
        prev.coverage.add(1,1);
        curr.coverage = new ArrayList<>();
        curr.coverage.add(0,null);
        curr.coverage.add(1,0);
        curr.modifiedLines = new HashSet<>();
        curr.modifiedLines.add(1);


        CoverallsImporter.DiffResult diff = curr.diff(prev);
        assertEquals(0,diff.modifiedLinesNewlyHit);
        assertEquals(1,diff.modifiedLinesNoLongerHit);
        assertEquals(0,diff.modifiedLinesStillHit);
    }

    @Test
    public void testmodifiedLinesStillHit() {
        prev.coverage = new ArrayList<>();
        prev.coverage.add(0,null);
        prev.coverage.add(1,1);
        curr.coverage = new ArrayList<>();
        curr.coverage.add(0,null);
        curr.coverage.add(1,1);
        curr.modifiedLines = new HashSet<>();
        curr.modifiedLines.add(1);


        CoverallsImporter.DiffResult diff = curr.diff(prev);
        assertEquals(0,diff.modifiedLinesNewlyHit);
        assertEquals(0,diff.modifiedLinesNoLongerHit);
        assertEquals(1,diff.modifiedLinesStillHit);
    }

    @Test
    public void testmodifiedLinesStillHitAllThree() {
        prev.coverage = new ArrayList<>();
        prev.coverage.add(0,null);
        prev.coverage.add(1,1);
        prev.coverage.add(2,0);
        prev.coverage.add(3,1);
        curr.coverage = new ArrayList<>();
        curr.coverage.add(0,null);
        curr.coverage.add(1,1);
        curr.coverage.add(2,1);
        curr.coverage.add(3,0);
        curr.modifiedLines = new HashSet<>();
        curr.modifiedLines.add(1);
        curr.modifiedLines.add(2);
        curr.modifiedLines.add(3);


        CoverallsImporter.DiffResult diff = curr.diff(prev);
        assertEquals(1,diff.modifiedLinesNewlyHit);
        assertEquals(1,diff.modifiedLinesNoLongerHit);
        assertEquals(1,diff.modifiedLinesStillHit);
    }
*/

    @Test
    public void testNewLinesHit() {
        prev.coverage = new ArrayList<>();
        prev.coverage.add(0,null);
        prev.coverage.add(1,1);
        curr.coverage = new ArrayList<>();
        curr.coverage.add(0,null);
        curr.coverage.add(1,0);
        curr.coverage.add(2,0);
        curr.coverage.add(3,1);
        curr.coverage.add(4,0);
        curr.newLines = new HashSet<>();
        curr.newLines.add(2);
        curr.newLines.add(3);
        curr.newLines.add(4);

        CoverallsImporter.DiffResult diff = curr.diff(prev);
        assertEquals(1,diff.newLinesHit);
        assertEquals(2,diff.newLinesNotHit);
    }

    @Test
    public void testNewLinesNotHit() {
        prev.coverage = new ArrayList<>();
        prev.coverage.add(0,null);
        prev.coverage.add(1,1);
        curr.coverage = new ArrayList<>();
        curr.coverage.add(0,null);
        curr.coverage.add(1,0);
        curr.coverage.add(2,0);
        curr.coverage.add(3,1);
        curr.coverage.add(4,0);
        curr.newLines = new HashSet<>();
        curr.newLines.add(2);
        curr.newLines.add(3);
        curr.newLines.add(4);

        CoverallsImporter.DiffResult diff = curr.diff(prev);
        assertEquals(1,diff.newLinesHit);
        assertEquals(2,diff.newLinesNotHit);
    }



    @Test
    public void testDeletedLinesHit() {
        prev.coverage = new ArrayList<>();
        prev.coverage.add(0,null);
        prev.coverage.add(1,1);
        prev.coverage.add(2,0);
        prev.coverage.add(3,1);
        prev.coverage.add(4,0);
        prev.coverage.add(5,0);
        curr.coverage = new ArrayList<>();
        curr.coverage.add(0,null);
        curr.coverage.add(1,0);
        curr.coverage.add(2,1);
        curr.deletedLines = new HashSet<>();
        curr.deletedLines.add(2);
        curr.deletedLines.add(3);
        curr.deletedLines.add(4);
        curr.lineMapping = new HashMap<>();
        curr.lineMapping.put(1,1);
        curr.lineMapping.put(5,2);



        CoverallsImporter.DiffResult diff = curr.diff(prev);
        assertEquals(1,diff.deletedLinesHit);
        assertEquals(2,diff.deletedLinesNotHit);

        assertEquals(3,diff.totalStatementsHitInEither);
        assertEquals(2,diff.totalStatementsHitPrev);
    }

    @Test
    public void testoldLinesNewlyHit() {
        prev.coverage = new ArrayList<>();
        prev.coverage.add(0,null);
        prev.coverage.add(1,1);
        prev.coverage.add(2,1);
        prev.coverage.add(3,1);
        prev.coverage.add(4,0);
        prev.coverage.add(5,0);
        curr.coverage = new ArrayList<>();
        curr.coverage.add(0,null);
        curr.coverage.add(1,0);
        curr.coverage.add(2,0);
        curr.coverage.add(3,0);
        curr.coverage.add(4,1);
        curr.coverage.add(5,1);




        CoverallsImporter.DiffResult diff = curr.diff(prev);
        assertEquals(2,diff.oldLinesNewlyHit);
        assertEquals(3,diff.oldLinesNoLongerHit);
    }


    @Test
    public void testoldLinesNewlyHitWithMapping() {
        prev.coverage = new ArrayList<>();
        prev.coverage.add(0,null);
        prev.coverage.add(1,1);
        prev.coverage.add(2,1);
        prev.coverage.add(3,1);
        prev.coverage.add(4,0);
        prev.coverage.add(5,0);
        prev.coverage.add(6,0);
        curr.coverage = new ArrayList<>();
        curr.coverage.add(0,null);
        curr.coverage.add(1,1);
        curr.coverage.add(2,0);
        curr.coverage.add(3,0);
        curr.coverage.add(4,0);
        curr.coverage.add(5,1);
        curr.deletedLines = new HashSet<>();
        curr.deletedLines.add(2);
        curr.lineMapping = new HashMap<>();
        curr.lineMapping.put(1,1);
        curr.lineMapping.put(3,2);
        curr.lineMapping.put(4,3);
        curr.lineMapping.put(5,4);
        curr.lineMapping.put(6,5);

        CoverallsImporter.DiffResult diff = curr.diff(prev);
        assertEquals(1,diff.oldLinesNewlyHit);
    }



    @Test
    public void testoldLinesNoLongerHitWithMapping() {
        prev.coverage = new ArrayList<>();
        prev.coverage.add(0,null);
        prev.coverage.add(1,1);
        prev.coverage.add(2,1);
        prev.coverage.add(3,1);
        prev.coverage.add(4,0);
        prev.coverage.add(5,0);
        prev.coverage.add(6,0);
        curr.coverage = new ArrayList<>();
        curr.coverage.add(0,null);
        curr.coverage.add(1,0);
        curr.coverage.add(2,0);
        curr.coverage.add(3,0);
        curr.coverage.add(4,0);
        curr.coverage.add(5,0);
        curr.deletedLines = new HashSet<>();
        curr.deletedLines.add(2);
        curr.lineMapping = new HashMap<>();
        curr.lineMapping.put(1,1);
        curr.lineMapping.put(2,3);
        curr.lineMapping.put(3,4);
        curr.lineMapping.put(4,5);
        curr.lineMapping.put(5,6);

        CoverallsImporter.DiffResult diff = curr.diff(prev);
        assertEquals(2,diff.oldLinesNoLongerHit);
    }


    @Test
    public void testoldLinesNoLongerHitWithMapping_0() {
        prev.coverage = new ArrayList<>();
        prev.coverage.add(0,null);
        prev.coverage.add(1,1);
        prev.coverage.add(2,1);
        prev.coverage.add(3,1);
        prev.coverage.add(4,0);
        prev.coverage.add(5,0);
        prev.coverage.add(6,0);
        curr.coverage = new ArrayList<>();
        curr.coverage.add(0,null);
        curr.coverage.add(1,1);
        curr.coverage.add(2,1);
        curr.coverage.add(3,0);
        curr.coverage.add(4,0);
        curr.coverage.add(5,0);
        curr.deletedLines = new HashSet<>();
        curr.deletedLines.add(2);
        curr.lineMapping = new HashMap<>();
        curr.lineMapping.put(1,1);
        curr.lineMapping.put(2,3);
        curr.lineMapping.put(3,4);
        curr.lineMapping.put(4,5);
        curr.lineMapping.put(5,6);

        CoverallsImporter.DiffResult diff = curr.diff(prev);
        assertEquals(0,diff.oldLinesNoLongerHit);
    }


    @Test
    public void testoldLinesNoLongerHitWithMapping_deleted() {
        prev.coverage = new ArrayList<>();
        prev.coverage.add(0,null);
        prev.coverage.add(1,0);
        prev.coverage.add(2,1);
        prev.coverage.add(3,0);
        prev.coverage.add(4,0);
        prev.coverage.add(5,0);
        prev.coverage.add(6,0);
        curr.coverage = new ArrayList<>();
        curr.coverage.add(0,null);
        curr.coverage.add(1,0);
        curr.coverage.add(2,0);
        curr.coverage.add(3,0);
        curr.coverage.add(4,0);
        curr.coverage.add(5,0);
        curr.deletedLines = new HashSet<>();
        curr.deletedLines.add(2);
        curr.lineMapping = new HashMap<>();
        curr.lineMapping.put(1,1);
        curr.lineMapping.put(2,3);
        curr.lineMapping.put(3,4);
        curr.lineMapping.put(4,5);
        curr.lineMapping.put(5,6);

        CoverallsImporter.DiffResult diff = curr.diff(prev);
        assertEquals(0,diff.oldLinesNoLongerHit);
    }



    @Test
    public void testnStatementsHitInBoth() {
        prev.coverage = new ArrayList<>();
        prev.coverage.add(0,null);
        prev.coverage.add(1,0);
        prev.coverage.add(2,0);
        prev.coverage.add(3,1);
        prev.coverage.add(4,0);
        prev.coverage.add(5,0);
        prev.coverage.add(6,0);
        curr.coverage = new ArrayList<>();
        curr.coverage.add(0,null);
        curr.coverage.add(1,0);
        curr.coverage.add(2,1);
        curr.coverage.add(3,0);
        curr.coverage.add(4,0);
        curr.coverage.add(5,0);
        curr.deletedLines = new HashSet<>();
        curr.deletedLines.add(2);
        curr.lineMapping = new HashMap<>();
        curr.lineMapping.put(1,1);
        curr.lineMapping.put(2,3);
        curr.lineMapping.put(3,4);
        curr.lineMapping.put(4,5);
        curr.lineMapping.put(5,6);

        CoverallsImporter.DiffResult diff = curr.diff(prev);
        assertEquals(1,diff.totalStatementsHitInBoth);
    }

    @Test
    public void testnStatementsHitInBoth2() {
        prev.coverage = new ArrayList<>();
        prev.coverage.add(0,null);
        prev.coverage.add(1,0);
        prev.coverage.add(2,1);
        prev.coverage.add(3,1);
        prev.coverage.add(4,0);
        prev.coverage.add(5,1);
        prev.coverage.add(6,0);
        curr.coverage = new ArrayList<>();
        curr.coverage.add(0,null);
        curr.coverage.add(1,0);
        curr.coverage.add(2,1);
        curr.coverage.add(3,1);
        curr.coverage.add(4,0);
        curr.coverage.add(5,1);
        curr.deletedLines = new HashSet<>();
        curr.deletedLines.add(2);
        curr.lineMapping = new HashMap<>();
        curr.lineMapping.put(1,1);
        curr.lineMapping.put(2,3);
        curr.lineMapping.put(3,4);
        curr.lineMapping.put(4,5);
        curr.lineMapping.put(5,6);

        CoverallsImporter.DiffResult diff = curr.diff(prev);
        assertEquals(1,diff.totalStatementsHitInBoth);
    }

    @Test
    public void testnStatementsHitInEither() {
        prev.coverage = new ArrayList<>();
        prev.coverage.add(0,null);
        prev.coverage.add(1,0);
        prev.coverage.add(2,1);
        prev.coverage.add(3,1);
        prev.coverage.add(4,0);
        prev.coverage.add(5,1);
        prev.coverage.add(6,0);
        curr.coverage = new ArrayList<>();
        curr.coverage.add(0,null);
        curr.coverage.add(1,0);
        curr.coverage.add(2,1);
        curr.coverage.add(3,1);
        curr.coverage.add(4,0);
        curr.coverage.add(5,1);
        curr.deletedLines = new HashSet<>();
        curr.deletedLines.add(2);
        curr.lineMapping = new HashMap<>();
        curr.lineMapping.put(1,1);
        curr.lineMapping.put(2,3);
        curr.lineMapping.put(3,4);
        curr.lineMapping.put(4,5);
        curr.lineMapping.put(5,6);

        CoverallsImporter.DiffResult diff = curr.diff(prev);
        assertEquals(5,diff.totalStatementsHitInEither);
    }

    @Test
    public void testNullBranch() {
        curr.coverage = new ArrayList<>();
        curr.coverage.add(0,null);
        curr.coverage.add(1,0);
        curr.coverage.add(2,1);
        curr.coverage.add(3,1);
        curr.coverage.add(4,0);
        curr.coverage.add(5,1);

        CoverallsImporter.DiffResult diff = curr.diff(null);
        // assertEquals(0,diff.modifiedLinesNewlyHit);
        // assertEquals(0,diff.modifiedLinesNoLongerHit);
        // assertEquals(0,diff.modifiedLinesStillHit);
        assertEquals(0,diff.newLinesHit);
        assertEquals(0,diff.newLinesNotHit);
        assertEquals(0,diff.deletedLinesHit);
        assertEquals(0,diff.deletedLinesNotHit);
        assertEquals(0,diff.oldLinesNewlyHit);
        assertEquals(0,diff.oldLinesNoLongerHit);
        assertEquals(0,diff.totalStatementsHitInBoth);
        assertEquals(3,diff.totalStatementsHitInEither);
        assertEquals(3,diff.totalStatementsHitNow);
        assertEquals(5,diff.totalStatementsNow);
        assertEquals(0,diff.totalStatementsHitPrev);
        assertEquals(0,diff.totalStatementsPrev);
    }


    /*@Test
    public void testModifiedLinesNewlyHit() {
        curr.coverage = new ArrayList<>();
        curr.coverage.add(0,null);
        curr.coverage.add(1,0);
        curr.coverage.add(2,1);
        curr.coverage.add(3,1);
        curr.coverage.add(4,0);
        curr.coverage.add(5,1);
        curr.modifiedLines = new HashSet<>();
        curr.modifiedLines.add(2);

        CoverallsImporter.DiffResult diff = curr.diff(null);
        assertEquals(1,diff.modifiedLinesNewlyHit);
    }*/

    @Test
    public void testnewLinesHit() {
        curr.coverage = new ArrayList<>();
        curr.coverage.add(0,null);
        curr.coverage.add(1,0);
        curr.coverage.add(2,1);
        curr.coverage.add(3,1);
        curr.coverage.add(4,0);
        curr.coverage.add(5,1);
//        curr.newLines = new HashSet<>();
//        curr.newLines.add(2);


        CoverallsImporter.DiffResult diff = curr.diff(null);
        assertEquals(0,diff.newLinesHit);
    }

/*
    @Test
    public void testmodifiedLinesNoLongerHit() {
        curr.coverage = new ArrayList<>();
        curr.coverage.add(0,null);
        curr.coverage.add(1,0);
        curr.coverage.add(2,0);
        curr.coverage.add(3,1);
        curr.coverage.add(4,0);
        curr.coverage.add(5,1);
        curr.modifiedLines = new HashSet<>();
        curr.modifiedLines.add(2);

        CoverallsImporter.DiffResult diff = curr.diff(null);
        assertEquals(1,diff.modifiedLinesNoLongerHit);
    }*/


    @Test
    public void testdeletedLinesNotHit() {
        curr.coverage = new ArrayList<>();
        curr.coverage.add(0,null);
        curr.coverage.add(1,0);
        curr.coverage.add(2,0);
        curr.coverage.add(3,1);
        curr.coverage.add(4,0);
        curr.coverage.add(5,1);
        curr.deletedLines = new HashSet<>();
        curr.deletedLines.add(2);
        curr.lineMapping = new HashMap<>();
        curr.lineMapping.put(1,1);
        curr.lineMapping.put(2,3);
        curr.lineMapping.put(3,4);
        curr.lineMapping.put(4,5);
        curr.lineMapping.put(5,6);

        CoverallsImporter.DiffResult diff = curr.diff(null);
        assertEquals(1,diff.deletedLinesNotHit);
    }

    @Test
    public void testCountPrevTotalLines_second() {
        prev.coverage = new ArrayList<>();
        prev.coverage.add(0,null);
        prev.coverage.add(1,0);
        curr.coverage = new ArrayList<>();
        curr.coverage.add(0,null);
        curr.deletedLines = new HashSet<>();
        curr.deletedLines.add(1);

        CoverallsImporter.DiffResult diff = curr.diff(prev);
        assertEquals(1,diff.totalStatementsPrev);
    }

    @Test
    public void testnewLinesNotHit_none() {
        prev.coverage = new ArrayList<>();
        prev.coverage.add(0,null);
        prev.coverage.add(1,0);
        curr.coverage = new ArrayList<>();
        curr.coverage.add(0,null);
        curr.coverage.add(1,0);

        CoverallsImporter.DiffResult diff = curr.diff(prev);
        assertEquals(0,diff.newLinesNotHit);
    }

    @Test
    public void testNullCurrDiff(){
        prev.coverage = new ArrayList<>();
        prev.coverage.add(0,null);
        prev.coverage.add(1,0);
        CoverallsImporter.SourceFile empty = new CoverallsImporter.SourceFile();
        empty.coverage = new ArrayList<>();
        CoverallsImporter.DiffResult diff = empty.diff(prev);
        assertEquals(0,diff.newLinesNotHit);
    }

}