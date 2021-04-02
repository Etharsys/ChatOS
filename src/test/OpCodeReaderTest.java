package test;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.ByteBuffer;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import reader.ConnectionRequestReader;
import reader.ErrorCodeReader;
import reader.OpCodeReader;
import reader.SendMessageAllReader;
import reader.SendPrivateMessageReader;

class OpCodeReaderTest {
	
	@Tag("OpCodeReader")
	@Test
	public void OpCodeShouldOnlyReadOneByte() {
		ByteBuffer bb = ByteBuffer.allocate(Short.BYTES);
		bb.put((byte)1);
		bb.put((byte)1);
		var OCR = new OpCodeReader();
		OCR.process(bb);
		assert(bb.position() == 1);
	}
	
	@Tag("OpCodeReader")
	@Test
	public void OpCodeShouldReturnCRWhenFacing1() {
		ByteBuffer bb = ByteBuffer.allocate(Short.BYTES);
		bb.put((byte)1);
		bb.put((byte)1);
		var OCR = new OpCodeReader();
		OCR.process(bb);
		assert(OCR.get() instanceof ConnectionRequestReader);
	}
	
	@Tag("OpCodeReader")
	@Test
	public void OpCodeShouldReturnSPMWhenFacing2() {
		ByteBuffer bb = ByteBuffer.allocate(Short.BYTES);
		bb.put((byte)2);
		bb.put((byte)1);
		var OCR = new OpCodeReader();
		OCR.process(bb);
		assert(OCR.get() instanceof SendPrivateMessageReader);
	}
	
	@Tag("OpCodeReader")
	@Test
	public void OpCodeShouldReturnSMAWhenFacing3() {
		ByteBuffer bb = ByteBuffer.allocate(Short.BYTES);
		bb.put((byte)3);
		bb.put((byte)1);
		var OCR = new OpCodeReader();
		OCR.process(bb);
		assert(OCR.get() instanceof SendMessageAllReader);
	}
	
	@Tag("OpCodeReader")
	@Test
	public void OpCodeShouldReturnERRORWhenFacing6() {
		ByteBuffer bb = ByteBuffer.allocate(Short.BYTES);
		bb.put((byte)6);
		bb.put((byte)1);
		var OCR = new OpCodeReader();
		OCR.process(bb);
		assert(OCR.get() instanceof ErrorCodeReader);
	}
	
	@Tag("OpCodeReader")
	@Test
	public void OpCodeShouldThrowIfUsingTwoTimesInARow() {
		ByteBuffer bb = ByteBuffer.allocate(Short.BYTES);
		bb.put((byte)6);
		bb.put((byte)1);
		var OCR = new OpCodeReader();
		OCR.process(bb);
		assertThrows(IllegalStateException.class, () -> OCR.process(bb));
	}
	
	@Tag("OpCodeReader")
	@Test
	public void OpCodeShouldThrowWhenAskingTooEarly() {
		ByteBuffer bb = ByteBuffer.allocate(Short.BYTES);
		bb.put((byte)6);
		bb.put((byte)1);
		var OCR = new OpCodeReader();
		assertThrows(IllegalStateException.class, () -> OCR.get());
	}
	
	@Tag("OpCodeReader")
	@Test
	public void OpCodeShouldCorrectlyReset() {
		ByteBuffer bb = ByteBuffer.allocate(Short.BYTES);
		bb.put((byte)6);
		bb.put((byte)1);
		var OCR = new OpCodeReader();
		OCR.process(bb);
		OCR.reset();
		assertThrows(IllegalStateException.class,()->OCR.get());
		OCR.process(bb);
		OCR.get();
	}
}
