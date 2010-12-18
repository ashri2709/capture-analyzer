package capanalyzer.netutils.build;


/**
 * The class implements udp packet.<br>
 * <br>
 * used both for sniffing and injecting.<br>
 * <br>
 * @author roni bar-yanai
 *
 */
public class UDPPacket extends IPPacket
{
	public static final int CALCUALTE_UDP_CHECKSUM = 0; 
	/*
	 * 0                                    15 16                                     32
	 *  -------------------------------------------------------------------------------
	 *  |                                    |                                         |
	 *  |  16 bit source port                |    16 bit destination port              |
	 *  |                                    |                                         |
	 *  -------------------------------------------------------------------------------
	 *  |                                    |                                         |
	 *  |   16 bit udp length (include hdr)  |  16 bit check sum                       |
	 *  |                                    |                                         |
	 *   ------------------------------------------------------------------------------
	 *  |                                                                              |
	 *  |                            data if any                                       |
	 *  |                                                                              |
	 *  -------------------------------------------------------------------------------- 
	 * 
	 * 
	 * constants offset for the start of the udp packet of the fields. 
	 */
	private static final int UDP_HEADER_LENGTH = 8;

	private static final int UDP_DST_PORT_POS = 2;

	private static final int UDP_DATA_LENGTH_POS = 4;

	private static final int UDP_CHECKSUM_POS = 2;

	// holds the packet source port
	private int mySrcPrt = -1;

	// holds the packet destination port
	private int myDstPort = -1;

	// the offset from the start of the packet (including iphdr + and ethhdr parts)
	protected int _udpOffset = 0;

	// the udp part length (including the hdr)
	// (the ip length - ip hdr length)
	protected int myUdpLn = 0;

	// the udp header length is constant
	protected int myUdpHdrLn = UDP_HEADER_LENGTH;

	// the udp checksum value.
	protected int myUdpChecksum = 0;

	// used when user build udp packet.
	// port must be set by the users.
	boolean _isWriteDstPort = true;
	boolean _isWriteSrcPort = true;
	
	/**
	 * create new udp packet for injection.
	 * 
	 */
	public UDPPacket()
	{
		_isReadDstPort = true;
		_isReadSrcPort = true;
		_isReadUDPChkSum = true;
		setIPProtocol(IPPacketType.UDP);
	}

	/**
	 * create udp packet from existing buffer. 
	 * @param thePacket - valid packet
	 */
	public UDPPacket(byte[] thePacket)
	{
		super(thePacket);
		_udpOffset = myIPHdrOffset + myIPHdrLength;
	}
	
	

	/**
	 * check if all needed fields are set.<br>
	 * this includes:<br>
	 * <br>
	 * 1. src ip and port.<br>
	 * 2. dst ip and port<br>
	 * 3. data filed<br>
	 * 
	 */
	public boolean isMandatoryFieldsSet()
	{
		if (super.isMandatoryFieldsSetNoData() == false || _isWriteDstPort == false || _isWriteSrcPort == false)
			return false;
		
		if (_udpDataBytes == null)
			return false;
			
		return true;
	}

	/**
	 * completes all not mandatory fields.<br>
	 * <br>
	 * mandatory fields:<br>
	 * 1. src ip and port.<br>
	 * 2. dst ip and port<br>
	 * 3. data filed <br>
	 * @Override
	 */
	public void atuoComplete()
	{
		super.atuoCompleteNoData();
	}
	
	boolean _isReadSrcPort = false;

	/**
	 * @return the source port number.
	 */
	public int getSourcePort()
	{
		if (_isReadSrcPort == false)
		{
			mySrcPrt = ByteUtils.getByteNetOrderTo_uint16(myPacket, _udpOffset);
			_isReadSrcPort = true;
		}
		return mySrcPrt;
	}

	/**
	 * Set the source port 
	 * @param thePort - 16 int number 0-65535
	 */
	public void setSrcPort(int thePort)
	{
		_isWriteSrcPort = true;
		_isReadSrcPort = true;
		mySrcPrt = thePort;
	}

	boolean _isReadDstPort = false;

	/**
	 * @return the destination port number.
	 */
	public int getDestinationPort()
	{
		if (_isReadDstPort == false)
		{
			_isReadDstPort = true;
			myDstPort = ByteUtils.getByteNetOrderTo_uint16(myPacket, _udpOffset + UDP_DST_PORT_POS);
		}
		return myDstPort;
	}
	
	/**
	 * set the udp packet destination port.
	 * @param thePort
	 */
	public void setDstPort(int thePort)
	{
		_isWriteDstPort = true;
		_isReadDstPort = true;
		myDstPort = thePort;
	}

	boolean _isReadUDPLength = false;
	/**
	 * @return the total length of the UDP packet, including header and
	 * data payload, in bytes.
	 */
	public int getUDPLength()
	{
		if (_isSniffedPkt && !_isReadUDPLength)
		{
		   myUdpLn = ByteUtils.getByteNetOrderTo_uint16(myPacket, _udpOffset + UDP_DATA_LENGTH_POS);
		}
		return myUdpLn;
	}
	
	boolean _isReadUDPChkSum = false;

	/**
	 * @return the header checksum.
	 */
	public int getUDPChecksum()
	{
		if (_isReadUDPChkSum == false)
		{
		  myUdpChecksum = ByteUtils.getByteNetOrderTo_uint16(myPacket, _udpOffset + UDP_CHECKSUM_POS);
		  _isReadUDPChkSum = true;
		}
		return myUdpChecksum;
	}
			
	/**
	 * set the udp check sum.
	 * if not set or set to CALCUALTE_UDP_CHECKSUM (equals zero)
	 * then will build it automatically.
	 * @param theCheckSum
	 */
	public void setUDPCheckSum(int theCheckSum)
	{
		_isReadUDPChkSum = true;
		myUdpChecksum = theCheckSum;
	}

	private byte[] _udpHeaderBytes = null;

	/** 
	 * @return the UDP header as a byte array.
	 */
	protected byte[] getUDPHeader()
	{
		if (_udpHeaderBytes == null && _isSniffedPkt)
		{
			_udpHeaderBytes = new byte[UDP_HEADER_LENGTH];
			System.arraycopy(myPacket, _udpOffset + myUdpHdrLn, _udpHeaderBytes, 0, _udpHeaderBytes.length);
		}
		return _udpHeaderBytes;
	}

	private byte[] _udpDataBytes = null;

	/**
	 * return the udp data as a byte array.
	 */
	public byte[] getUDPData()
	{
		if (_udpDataBytes == null && _isSniffedPkt)
		{
			_udpDataBytes = new byte[getUDPLength() - UDP_HEADER_LENGTH];
			System.arraycopy(myPacket, _udpOffset + UDP_HEADER_LENGTH, _udpDataBytes, 0, _udpDataBytes.length);
		}
		return _udpDataBytes;
	}
	
	/**
	 * 
	 * @return the payload length
	 */
	public int getUDPDataLength()
	{
		byte[] data = getUDPData();
		if (data == null)
		{
			return 0;
		}
		return data.length;
	}
	
	/**
	 * In this case will return the updhdr+upd data.
	 * when sniffed packet.
	 * when build packet this method will return null.
	 */
	public byte[] getIPData()
	{
		if (_isSniffedPkt)
		{
			byte[] tmp = new byte[getUDPLength()];
			System.arraycopy(myPacket, _udpOffset, tmp, 0, tmp.length);
			return tmp;
		}
		return null;
	}
	
	/**
	 * 
	 * @override the ip set data.
	 */
	public void setData(byte data[])
	{
		if (data != null)
		{
			_udpDataBytes = data;
			myUdpLn = myUdpHdrLn+data.length;
			setTotaIPlLength(myUdpLn+getIPHeaderLength());
		}
	}

	/*
	 *  UDP and TCP include a 12-byte pseudo-header with the UDP datagram (or TCP segment)
	 *  just for the checksum computation. This pseudo-header includes certain fields from the IP header.
	 * 
	 *  the 12 byte include:
	 *  0-4 : the ip src 
	 *  5-8 : the ip dst
	 *  9 : zero byte
	 *  10 : the protocl type 0x11 for udp
	 *  11 - 12 : the total length of the udp packet.
	 *  
	 *  constants for udp packet pseudo header
	 */
	private static final int PSEUDO_HDR_LEN = 12;

	private static final int PSEUDO_IP_SRC_POS = 0;

	private static final int PSEUDO_IP_DST_POS = 4;

	private static final int PSEUDO_ZERO_POS = 8;

	private static final int PSEUDO_PROTO_CODE_POS = 9;

	private static final int PSEUDO_UDP_LENGTH_POS = 10;

	private static final int IP_ADDR_LENGTH = 4;

	private static final int UDP_LEGTH_LENGTH = 2;

	/**
	 * @return the pseudo header for the packet.
	 */
	protected byte[] buildPseudoHeader()
	{
		byte[] _pseudo_header = new byte[PSEUDO_HDR_LEN];
		ByteUtils.setBigIndianInBytesArray(_pseudo_header, PSEUDO_IP_SRC_POS, getSourceIP(), IP_ADDR_LENGTH);
		ByteUtils.setBigIndianInBytesArray(_pseudo_header, PSEUDO_IP_DST_POS, getDestinationIP(), IP_ADDR_LENGTH);
		_pseudo_header[PSEUDO_ZERO_POS] = 0;
		_pseudo_header[PSEUDO_PROTO_CODE_POS] = IPPacketType.UDP;
		ByteUtils.setBigIndianInBytesArray(_pseudo_header, PSEUDO_UDP_LENGTH_POS, getUDPLength(), UDP_LEGTH_LENGTH);
		return _pseudo_header;
	}

	protected int getUDPChksum()
	{
		byte[] _pseudo_header = buildPseudoHeader();

		int sum = 0;

		// first run on the pseudo header 
		for (int i = 0; i < _pseudo_header.length; i += 2)
		{
			int byte1Val = _pseudo_header[i] & 0xff;
			int byte2Val = (i + 1 < _pseudo_header.length) ? _pseudo_header[i + 1] & 0xff : 0;
			sum = sum + ((byte1Val << 8) + byte2Val);
		}

		// run on the udp part
		for (int i = _udpOffset; i < myPacket.length; i += 2)
		{
			int byte1Val = myPacket[i] & 0xff;
			int byte2Val = (i + 1 < myPacket.length) ? myPacket[i + 1] & 0xff : 0;
			sum = sum + ((byte1Val << 8) + byte2Val);
		}

		sum = (sum >> 16) + (sum & 0xffff);
		sum = sum + (sum >> 16);
		sum = ~sum & 0xffff;

		return sum;
	}
}
