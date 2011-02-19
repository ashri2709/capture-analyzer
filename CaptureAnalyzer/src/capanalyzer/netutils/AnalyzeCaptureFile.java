package capanalyzer.netutils;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import capanalyzer.database.ConnectionPool;
import capanalyzer.netutils.build.FiveTuple;
import capanalyzer.netutils.build.IPPacket;
import capanalyzer.netutils.files.CaptureFileBlock;
import capanalyzer.netutils.files.CaptureFileFactory;
import capanalyzer.netutils.files.CaptureFileReader;

public class AnalyzeCaptureFile
{
	//static String erfFile = "e:\\capture_012_15_06_2009.erf";
	static String erfFile = "d:\\capture_012_15_06_2009.erf";

	/** JDBC driver name */
	private static String driverName;

	/** JDBC connection URL */
	private static String connURL;

	/** JDBC connection username */
	private static String username;

	/** JDBC connection password */
	private static String password;

	@SuppressWarnings("serial")
	public static void main(String[] args) throws IOException, NetUtilsException
	{
		int[] numOfDbPatitions = new int[]{1, 10, 20, 40};
		int[] sizeOfBuffer = new int[]{8, 16, 32, 64};
		int[] numOfMaps = new int[]{2, 4, 8, 16};
		
		for (int k = 0; k < numOfDbPatitions.length; k++)
		{
			for (int l = 0; l < sizeOfBuffer.length; l++)
			{
				for (int m = 0; m < numOfMaps.length; m++)
				{
			
					Statisics.setNumOfDbPartitions(numOfDbPatitions[k]);
					Statisics.setSizeOfBuffer(sizeOfBuffer[l]);
					Statisics.setNumOfMaps(numOfMaps[m]);
					
					boolean takeReadAndStoreInMapTime = true;
					Statisics.initStatsArrays();
					
					long agingTime = 120 * 1000000;
					List<IPacketAnalyzer> packetAnalyzers = new ArrayList<IPacketAnalyzer>();
					packetAnalyzers.add(new BaseAnalyzer());
			
					CaptureFileReader frd = CaptureFileFactory.createCaptureFileReader(erfFile);
					CaptureFileBlock nextblock = null;
					long firstPacketTime = 0, currentPacketTime = 0;
					long counter = 0;
			
					FlowsDataStructure flowsDataStructure = FlowsDataStructure.getInstance();
					FlowsDataStructureForDB flowsDataStructureForDb = FlowsDataStructureForDB.getInstance();
					List<FiveTuple> listOfFlowsThatShouldAgeFiveTuples;
			
					setMySQLConnectInfo();
					// SQLExecutor sqlExec2 = new SQLExecutor(getConnectionPool());
					Connection con = getConnectionPool().getConnection();
					
					try
					{
						con.createStatement().execute("DROP TABLE IF EXISTS `capture_analyzer`.`all_flows`");

						con.createStatement()
								.execute(
										"CREATE TABLE  `capture_analyzer`.`all_flows` " +
										"(`flow_id` int(10) unsigned NOT NULL DEFAULT '0'," +
										"`source_ip` bigint(20) unsigned NOT NULL DEFAULT '0'," +
										"`source_port` int(10) unsigned NOT NULL DEFAULT '0'," +
										"`destination_ip` bigint(20) unsigned NOT NULL DEFAULT '0'," +
										"`destination_port` int(10) unsigned NOT NULL DEFAULT '0'," +
										"`flow_type` int(10) unsigned NOT NULL DEFAULT '0'," +
										"`start_time` bigint(20) unsigned NOT NULL DEFAULT '0'," +
										"`duration` bigint(20) unsigned NOT NULL DEFAULT '0'," +
										"`number_of_packets` bigint(20) unsigned NOT NULL DEFAULT '0'," +
										"`size` int(10) unsigned NOT NULL DEFAULT '0'," +
										"`min_packet_size` int(10) unsigned NOT NULL DEFAULT '0'," +
										"`average_packet_size` int(10) unsigned NOT NULL DEFAULT '0'," +
										"`max_packet_size` int(10) unsigned NOT NULL DEFAULT '0'," +
										"`min_ipg` bigint(20) unsigned NOT NULL DEFAULT '0'," +
										"`average_ipg` bigint(20) unsigned NOT NULL DEFAULT '0'," +
										"`max_ipg` bigint(20) unsigned NOT NULL DEFAULT '0'," +
										"PRIMARY KEY (`flow_id`) ENGINE=InnoDB DEFAULT CHARSET=latin1 " +
										"PARTITION BY HASH(flow_id) " +
										"PARTITIONS " + Statisics.getNumOfDbPartitions() + "; ");
					} catch (SQLException e1)
					{
						e1.printStackTrace();
					}
			
					try
					{
						long beforeReadAndStoreInMapTime = 0;
						long startTime = System.currentTimeMillis();
						while ((nextblock = frd.readNextBlock()) != null)
						{
							if(takeReadAndStoreInMapTime == true)
							{
								takeReadAndStoreInMapTime = false;
								beforeReadAndStoreInMapTime = System.currentTimeMillis();
							}
								
								
							if (IPPacket.statIsIpPacket(nextblock.getMyData()))
							{
								for (IPacketAnalyzer packetAnalyzer : packetAnalyzers)
								{
									packetAnalyzer.processPacket(nextblock);
								}
			
								if (counter == 0)
									firstPacketTime = nextblock.getMyPktHdr().getTime();
			
								counter++;
								if (counter % 1000000 == 0)
								{
									long afterReadAndStoreInMapTime = System.currentTimeMillis();
									Statisics.addStatToReadAndStoreInMap(afterReadAndStoreInMapTime-beforeReadAndStoreInMapTime);
									System.out.println("ReadAndStoreInMapTime= " + (afterReadAndStoreInMapTime-beforeReadAndStoreInMapTime));
									takeReadAndStoreInMapTime = true;
									
									System.out.println("Percentage Done: " + frd.getBytesRead() / (float) frd.getCapFileSizeInBytes());
			
									currentPacketTime = nextblock.getMyPktHdr().getTime();
									listOfFlowsThatShouldAgeFiveTuples = flowsDataStructure.getAllFlowsThatShouldAgeFiveTuples(currentPacketTime, agingTime);
			
									System.out.println("Real packet time from start: " + (long)(((currentPacketTime - firstPacketTime) / (long)(1000000 * 60 * 60)) % 24L) + ":" + (long)((currentPacketTime - firstPacketTime) / (long)(1000000 * 60) % 60L) + ":" + (long)(((currentPacketTime - firstPacketTime) / 1000000L) % 60L));
			
									for (FiveTuple fiveTuple : listOfFlowsThatShouldAgeFiveTuples)
									{
										for (IPacketAnalyzer packetAnalyzer : packetAnalyzers)
										{
											packetAnalyzer.finalizeFlow(fiveTuple);
										}
									}
			
									System.out.print("Nmber of lines in struct = ");
									long total = 0;
									long[] mapSizes = flowsDataStructure.getSizesOfAllMaps();
									for (int i = 0; i < mapSizes.length; i++)
									{
										System.out.print(mapSizes[i] + ", ");
										total += mapSizes[i];
									}
									System.out.print("Total = " + total);
									System.out.println("\n");
			
									List<String> allResultNames = new ArrayList<String>() {};
									PreparedStatement pstmt = null;
									if (listOfFlowsThatShouldAgeFiveTuples.size() > 0)
									{
										String resultNames = "";
										String resultValues = "";
			
										Map<String, Integer> resultIntegerMap = flowsDataStructureForDb.getFlowInfoForDbStruct(listOfFlowsThatShouldAgeFiveTuples.get(0)).getIntegerMap();
										Map<String, Long> resultLongMap = flowsDataStructureForDb.getFlowInfoForDbStruct(listOfFlowsThatShouldAgeFiveTuples.get(0)).getLongMap();
										Set<String> allIntResults = resultIntegerMap.keySet();
										Set<String> allLongResults = resultLongMap.keySet();
										
										for (String intKey : allIntResults)
											allResultNames.add(intKey);
										for (String longKey : allLongResults)
											allResultNames.add(longKey);
			
										for (String resultName : allResultNames)
										{
											resultNames += resultName + ",";
											resultValues += "?,";
										}
										resultNames = resultNames.substring(0, resultNames.length() - 1);
										resultValues = resultValues.substring(0, resultValues.length() - 1);
										
										
										long beforeInsertTime = System.currentTimeMillis();
										pstmt = con.prepareStatement("INSERT INTO all_flows(" + resultNames + ") VALUES (" + resultValues + ")");
										con.setAutoCommit(false);
										con.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
								
										for (FiveTuple fiveTuple : listOfFlowsThatShouldAgeFiveTuples)
										{
											resultIntegerMap = flowsDataStructureForDb.getFlowInfoForDbStruct(fiveTuple).getIntegerMap();
											resultLongMap = flowsDataStructureForDb.getFlowInfoForDbStruct(fiveTuple).getLongMap();
			
											for (int j = 0; j < allResultNames.size(); j++)
											{
												if (resultIntegerMap.containsKey(allResultNames.get(j)))
													pstmt.setInt(j + 1, resultIntegerMap.get(allResultNames.get(j)));
												else if (resultLongMap.containsKey(allResultNames.get(j)))
													pstmt.setLong(j + 1, resultLongMap.get(allResultNames.get(j)));
											}
											pstmt.addBatch();
								
											resultIntegerMap.clear();
											resultLongMap.clear();
											resultIntegerMap = null;
											resultLongMap = null;
											flowsDataStructureForDb.removeFlow(fiveTuple);
										}
								
										pstmt.executeBatch();
										con.commit();
										pstmt.clearBatch();
										pstmt.close();
										long afterInsertTime = System.currentTimeMillis();
										Statisics.addStatToDbInserts(afterInsertTime-beforeInsertTime);
										System.out.println("DbInsertsTime= " + (afterInsertTime-beforeInsertTime));
										
										listOfFlowsThatShouldAgeFiveTuples.clear();
										allResultNames.clear();
										allIntResults.clear();
										allLongResults.clear();
										listOfFlowsThatShouldAgeFiveTuples = null;
										allResultNames = null;
										allIntResults = null;
										allLongResults = null;
									}
									
									System.gc();
								}
							}
						}
			
						System.out.println("Total Time = " + (System.currentTimeMillis() - startTime) / 1000 + " Seconds");
						Statisics.exportToCsvFile("c:\\CaptureAnalyzerStats_numPart_" + Statisics.getNumOfDbPartitions() + "_numMaps_" + Statisics.getNumOfMaps() + "_BufSize_" + Statisics.getSizeOfBuffer() + "__Total_" + ((System.currentTimeMillis() - startTime) / 1000) +".csv");
			
					} catch (Exception e)
					{
						System.out.println("Exception Caught");
						e.printStackTrace();
					}
				}
			}
		}
	}

	/**
	 * Creates a new connection pool object
	 * 
	 * @return ConnectionPool
	 */
	public static ConnectionPool getConnectionPool()
	{
		return (new ConnectionPool(1, driverName, connURL, username, password));
	}

	/**
	 * Sets fields to values required to connect to a sample MySQL database
	 */
	private static void setMySQLConnectInfo()
	{
		driverName = "com.mysql.jdbc.Driver";
		connURL = "jdbc:mysql://localhost/capture_analyzer";
		username = "root";
		password = "oklydokly";
	}
}