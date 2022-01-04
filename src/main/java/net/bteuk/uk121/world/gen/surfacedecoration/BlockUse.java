package net.bteuk.uk121.world.gen.surfacedecoration;

/**
 * Identifies what each block in a 16x16 chunk represents
 */

import net.bteuk.uk121.TerraConstants;
import net.bteuk.uk121.world.gen.Projections.ModifiedAirocean;
import net.bteuk.uk121.world.gen.surfacedecoration.geojson.TileGrid;
import net.bteuk.uk121.world.gen.surfacedecoration.overpassapi.*;
import net.bteuk.uk121.world.gen.surfacedecoration.overpassapi.Object;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

public class BlockUse
{
    public UseType[][] grid;
    private ArrayList<Object> objects;
    private ArrayList<Way> ways;
    private BoundingBox bbox;
    private int[] blockMins;
    private ModifiedAirocean projection;

    public BlockUse(BoundingBox bbox, int[] blockMins, ModifiedAirocean projection)
    {
        grid = new UseType[48][48];
    //    objects = new ArrayList<Object>();
        ways = new ArrayList<Way>();
        this.bbox = bbox;
        this.blockMins = blockMins;
        this.projection = projection;
    }

    public BlockUse(UseType useType)
    {
        grid = new UseType[48][48];
        clearVoid(useType);
    }

    public static void main(String[] args)
    {
        BoundingBox bbox = new BoundingBox(51.43757, 51.43829, 0.38353, 0.38454);
        int[] blockMins = {0, 0};
        BlockUse BU = new BlockUse(bbox , blockMins, TerraConstants.projection);

        BU.fillGrid(false);

     //   BU.display();
    }

    public UseType[][] getGrid()
    {
        return grid;
    }

    public void fillGrid(boolean bAlternative)
    {
    //    Calendar cal = Calendar.getInstance();
     //   Date time = cal.getTime();
    //    long lTime1 = time.getTime();

 //       ways = GetOSM.entry(bbox, bAlternative);

        TileGrid tileGrid = new TileGrid(bbox);
        tileGrid.getInfo();
        ways = tileGrid.readInfoToWays();

     //   cal = Calendar.getInstance();
    //    time = cal.getTime();
     //   long lTime2 = time.getTime();

    /*    if (bAlternative)
            System.out.println("Got "+ways.size() +" ways, alt api: " + (lTime2 - lTime1) + "ms");
        else
            System.out.println("Got "+ways.size() +" ways: " + (lTime2 - lTime1) + "ms");

     */
        int i;

        //Goes through each "way" in the data
        for (i = 0 ; i < ways.size() ; i++)
        {
            //Imports the tags and nodes of the way
            Way way = ways.get(i);
            ArrayList<Tag> tags = way.getTags();
            ArrayList<Node> nodes = way.getNodes();

        //    System.out.println("ID: "+way.getId());

            UseType useType = UseType.Land;
            boolean bHighway = false;

            for (Tag tag: tags)
            {
                //Checks tag keys for highway and if found, stop searching tags and deal with the way as a road
                if (tag.key.equals("highway"))
                {
                    bHighway = true;

                    switch (tag.value)
                    {
                        case "motorway":
                            useType = UseType.Motorway;
                            break;
                        case "trunk":
                        case "primary":
                            useType = UseType.Primary;
                            break;
                        case "secondary":
                            useType = UseType.Secondary;
                            break;
                        case "track":
                            useType = UseType.Track;
                            break;
                        case "footway":
                        case "cycleway":
                        case "bridleway":
                        case "path":
                            useType = UseType.Footway;
                            break;
                        case "tertiary":
                        default:
                            useType = UseType.Tertiary;
                            break;
                    }
                }
                else if (tag.key.equals("building"))
                {
                    useType = UseType.BuildingOutline;
                    break;
                }
            }

            //Deal with building or highway
            if (bHighway || useType == UseType.BuildingOutline)
            {
                //Stores the block coordinates of each of the nodes
                int[][] iNodeBlocks = new int[nodes.size()][2];
                int iCount = 0;

                //Goes through each node and adds it to the node blocks array
                for (Node node : nodes)
                {
                    double[] MCcoords = projection.fromGeo(node.longitude, node.latitude);

                    iNodeBlocks[iCount][0] = (int) (MCcoords[0] - blockMins[0])/1;
                    iNodeBlocks[iCount][1] = (int) (MCcoords[1] - blockMins[1])/1;
                  //        System.out.println("The blocks of the downloaded node:");
                  //         System.out.println(iNodeBlocks[iCount][1]);

                    if (iNodeBlocks[iCount][0] >= 0 && iNodeBlocks[iCount][0] < 48 && iNodeBlocks[iCount][1]>= 0 && iNodeBlocks[iCount][1] < 48)
                    {
                        grid[iNodeBlocks[iCount][0]][iNodeBlocks[iCount][1]] = useType;
                    }

                    iCount++;
                }

                //Go through each node
            /*    for (int j = 0 ; j < iCount - 1 ; j++)
                {
                    nextNode(iNodeBlocks, j, iCount, 0, 0, useType);
                }
            */
                //Go through each node
                Line line = new Line();
                for (int j = 1 ; j < iCount ; j++)
                {
                    ArrayList<BlockVector3> vset =  line.drawLine(iNodeBlocks[j - 1][0], iNodeBlocks[j - 1][1], iNodeBlocks[j][0], iNodeBlocks[j][1]);
                    for (int k = 0 ; k < vset.size() ; k++)
                    {
                        int iBestBlockX = vset.get(k).getBlockX();
                        int iBestBlockZ = vset.get(k).getBlockZ();

                        if (iBestBlockX >= 0 && iBestBlockX < 48 && iBestBlockZ>= 0 && iBestBlockZ < 48)
                        {
                            grid[iBestBlockX][iBestBlockZ] = useType;
                        }
                    }
                }
            }
        }
        //Fills the rest of the grid with land
        clearRemaining();
    }

    private void nextNode(int[][] iNodeBlocks, int j, int iCount, int xOffset, int zOffset, UseType useType)
    {
        int iDistanceToNext;
        int iXComp, iZComp;

        int iShortestDistanceToNext = 999999999;
        int iBestBlockX = iNodeBlocks[j][0];
        int iBestBlockZ = iNodeBlocks[j][1];

        boolean bEnd = false;

        int xNewOffset = 0;
        int zNewOffset = 0;

        //Test every block around that node to test the closest block to the next node
        for (int x = xOffset - 1 ; x <= xOffset + 1 ; x++)
        {
            for (int z = zOffset - 1 ; z <= zOffset + 1 ; z++)
            {
                //Skip the node itself
                if (x == 0 && z == 0)
                    continue;

                //If this neighbouring block is the block of node 2, then that's it, we need to do no more
                if (iNodeBlocks[j][0]+x == iNodeBlocks[(j+1)][0] && iNodeBlocks[j][1]+z == iNodeBlocks[(j+1)][1])
                {
                    bEnd = true; //This is where the recursion ends. Once it reaches the next block
                    break;
                }

                iXComp = (iNodeBlocks[j][0]+x)-(iNodeBlocks[(j+1)][0]);
                iZComp = (iNodeBlocks[j][1]+z)-(iNodeBlocks[(j+1)][1]);
                iDistanceToNext = iXComp*iXComp + iZComp*iZComp;

                if (iDistanceToNext < iShortestDistanceToNext)
                {
                    iShortestDistanceToNext = iDistanceToNext;
                    //Best block in chunk
                    iBestBlockX = (iNodeBlocks[j][0]+x);
                    iBestBlockZ = (iNodeBlocks[j][1]+z);

                    //Offset from the node
                    xNewOffset = x;
                    zNewOffset = z;
                }
            }
            if (bEnd)
                break;
        }

        if (bEnd)
        {
            return;
        }
        else
        {
            if (iBestBlockX >= 0 && iBestBlockX < 48 && iBestBlockZ>= 0 && iBestBlockZ < 48)
            {
                grid[iBestBlockX][iBestBlockZ] = useType;
            }

            //Always then goes to the next node
            nextNode(iNodeBlocks, j, iCount, xNewOffset, zNewOffset, useType);

            //  else return;
        }
    }

    private void clearRemaining()
    {
        int i, j, k, l;

        float fThickness;

        for (i = 0 ; i < 48 ; i++)
        {
            for (j = 0 ; j < 48 ; j++)
            {
                if (grid[i][j] == null)
                {
                    grid[i][j] = UseType.Land;
                }

                switch (grid[i][j])
                {
                    case Motorway:
                        fThickness = 9;
                        for (k = -10 ; k < 10 ; k++)
                        {
                            for (l = -10; l < 10 ; l++)
                            {
                                if (l == 0 && k == 0)
                                    continue;
                                //If a block is 4 blocks distance from a road node, set its value to road derived
                                if ((i+k)<48 && (j+l)<48 && (i+k)>=0 && (j+l)>=0 && (k*k + l*l) < fThickness*fThickness)
                                {
                                    if (grid[i+k][j+l] != UseType.Motorway)
                                    {
                                        grid[i+k][j+l] = UseType.MotorwayDerived;
                                    }
                                }
                            }
                        }
                        break;

                    case Primary:
                        fThickness = 7;
                        for (k = -8 ; k < 8 ; k++)
                        {
                            for (l = -8; l < 8 ; l++)
                            {
                                if (l == 0 && k == 0)
                                    continue;
                                //If a block is 4 blocks distance from a road node, set its value to road derived
                                if ((i+k)<48 && (j+l)<48 && (i+k)>=0 && (j+l)>=0 && (k*k + l*l) < fThickness*fThickness)
                                {
                                    if (grid[i+k][j+l] != UseType.Primary)
                                    {
                                        grid[i+k][j+l] = UseType.PrimaryDerived;
                                    }
                                }
                            }
                        }
                        break;

                    case Secondary:
                        fThickness = 6;
                        for (k = -7 ; k < 7 ; k++)
                        {
                            for (l = -7; l < 7 ; l++)
                            {
                                if (l == 0 && k == 0)
                                    continue;
                                //If a block is 4 blocks distance from a road node, set its value to road derived
                                if ((i+k)<48 && (j+l)<48 && (i+k)>=0 && (j+l)>=0 && (k*k + l*l) < fThickness*fThickness)
                                {
                                    if (grid[i+k][j+l] != UseType.Secondary)
                                    {
                                        grid[i+k][j+l] = UseType.SecondaryDerived;
                                    }
                                }
                            }
                        }
                        break;

                    case Tertiary:
                        fThickness = 4;
                        for (k = -5 ; k < 5 ; k++)
                        {
                            for (l = -5; l < 5 ; l++)
                            {
                                if (l == 0 && k == 0)
                                    continue;
                                //If a block is 4 blocks distance from a road node, set its value to road derived
                                if ((i+k)<48 && (j+l)<48 && (i+k)>=0 && (j+l)>=0 && (k*k + l*l) < fThickness*fThickness)
                                {
                                    if (grid[i+k][j+l] != UseType.Tertiary)
                                    {
                                        grid[i+k][j+l] = UseType.TertiaryDerived;
                                    }
                                }
                            }
                        }
                        break;

                    case Footway:
                        fThickness = 1;
                        for (k = -2 ; k < 2 ; k++)
                        {
                            for (l = -2; l < 2 ; l++)
                            {
                                if (l == 0 && k == 0)
                                    continue;
                                //If a block is 4 blocks distance from a road node, set its value to road derived
                                if ((i+k)<48 && (j+l)<48 && (i+k)>=0 && (j+l)>=0 && (k*k + l*l) < fThickness*fThickness)
                                {
                                    if (grid[i+k][j+l] != UseType.Footway)
                                    {
                                        grid[i+k][j+l] = UseType.FootwayDerived;
                                    }
                                }
                            }
                        }
                        break;

                    case Track:
                        fThickness = 3;
                        for (k = -4 ; k < 4 ; k++)
                        {
                            for (l = -4; l < 4 ; l++)
                            {
                                if (l == 0 && k == 0)
                                    continue;
                                //If a block is 4 blocks distance from a road node, set its value to road derived
                                if ((i+k)<48 && (j+l)<48 && (i+k)>=0 && (j+l)>=0 && (k*k + l*l) < fThickness*fThickness)
                                {
                                    if (grid[i+k][j+l] != UseType.Track)
                                    {
                                        grid[i+k][j+l] = UseType.TrackDerived;
                                    }
                                }
                            }
                        }
                        break;
                }
            }
        }
    }

    private void clearVoid(UseType useType)
    {
        int i, j;
        for (i = 0 ; i < 48 ; i++)
        {
            for (j = 0 ; j < 48 ; j++)
            {
                grid[i][j] = useType;
            }
        }
    }

}
