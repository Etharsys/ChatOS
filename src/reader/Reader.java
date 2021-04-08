package reader;


import java.nio.ByteBuffer;

public interface Reader<T> {

	/**
	 * Reader status
	 */
    public static enum ProcessStatus {DONE,REFILL,ERROR};

    /**
     * 
     * @brief process the bytebuffer in parameter and update the status
     * @param bb the bytebuffer to process
     * @return the updated status
     */
    public ProcessStatus process(ByteBuffer bb);

    /**
     * 
     * @brief get the reader result value (datagram)
     * @return the datagram
     */
    public T get();

    /**
     * 
     * @brief reset the reader
     */
    public void reset();
}