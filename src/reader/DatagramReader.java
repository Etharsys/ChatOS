package reader;

import java.nio.ByteBuffer;

import reader.Reader.ProcessStatus;

/**
 * Interface representing datagrams readers able to read datagrams without OpCode
 * */
public interface DatagramReader{
	
	public ProcessStatus process(ByteBuffer bb);
	
	public void accept(DatagramVisitor visitor);
}
