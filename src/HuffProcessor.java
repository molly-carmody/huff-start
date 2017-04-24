import java.util.PriorityQueue;




/**
 *	Interface that all compression suites must implement. That is they must be
 *	able to compress a file and also reverse/decompress that process.
 * 
 *	@author Brian Lavallee
 *	@since 5 November 2015
 *  @author Owen Atrachan
 *  @since December 1, 2016
 */
public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); // or 256
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;
	public static final int HUFF_COUNTS = HUFF_NUMBER | 2;

	public enum Header{TREE_HEADER, COUNT_HEADER};
	public Header myHeader = Header.TREE_HEADER;
	public String[] codings;
	public int[] ret;


	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out){
		if(in == null||out == null){ //check inputs
			throw new NullPointerException("null in or out stream");
		}
		//goes through the bits and stores their frequency
		ret = new int[256]; //create array of int values where each entry is a counter for an 8-bit sequence
		while(true){
			int val = in.readBits(BITS_PER_WORD);
			if(val == -1){ //if invalid value break
				break;
			}
			ret[val]++; //ups frequency
		}
		in.reset();





		//perform the various methods below (as described in the respective method's section)
		HuffNode root = makeTreeFromCounts(ret);
		codings = new String[257];
		out.writeBits(BITS_PER_INT,HUFF_NUMBER);
		writeHeader(root,out);
		makeCodingsFromTree(root,"");
		writeCompressedBits(in,codings,out);
	}
	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */

	public void decompress(BitInputStream in, BitOutputStream out){
		if(in ==null||out==null){ //check inputs
			throw new NullPointerException("null in or out stream");
		}
		int val = in.readBits(BITS_PER_INT);
		if(val!=HUFF_NUMBER&&val!=HUFF_TREE){ //checks if it is a compressed file
			throw new HuffException("root doesn't have valid huff number");
		}
		HuffNode root = readTreeHeader(in);
		readCompressedBits(in, out, root);   
	}

	public void setHeader(Header header) {
		myHeader = header;
		System.out.println("header set to "+myHeader);
	}

	//creates a Hufftree based on the ret frequency counts
	public HuffNode makeTreeFromCounts(int [] a){
		if(a == null){ //checks input
			throw new NullPointerException("no string array");
		}

		//makes a priority queue of the value and frequency
		PriorityQueue<HuffNode> pq = new PriorityQueue<>();
		for(int i = 0; i < 256; i++) {
			if(a[i] != 0) {
				pq.add(new HuffNode(i, a[i]));
			}
		}

		//creates the "end" node
		HuffNode pseudo = new HuffNode(PSEUDO_EOF, 0);
		pq.add(pseudo);

		//goes through priority queue and creates a hufftree using pre-order traversal
		while(pq.size() > 1){ //while there are values to add
			HuffNode left = pq.remove();
			HuffNode right = pq.remove();
			HuffNode  t = new HuffNode(-1, left.weight() + right.weight(), left,right);
			pq.add(t);	
		}
		HuffNode root = pq.remove();
		return root;

	}

	public void makeCodingsFromTree(HuffNode hu, String path){
		if(path == null){ //checks input
			throw new NullPointerException("path is null");
		}
		if(hu == null){ //checks input
			throw new NullPointerException("null HuffNode");
		}
		if(hu.left() == null && hu.right() == null){//if you've reached the end, add this path to codings
			codings[hu.value()] = path; 
			return;
		}
		//if it has a left branch, go down and add a 0 to the path
		makeCodingsFromTree(hu.left(),path +"0");

		//else if it has a right branch, go down and add a 1 to the path
		makeCodingsFromTree(hu.right(),path +"1");
	}

	//
	public void writeHeader(HuffNode root, BitOutputStream out){
		if(out == null){ //checks input
			throw new NullPointerException("null out stream");}
		if(root == null){ //checks input
			throw new NullPointerException("null root");}

		if(root.left()!=null && root.right()!=null){ //if its an internal node
			out.writeBits(1, 0);
			writeHeader(root.left(),out);
			writeHeader(root.right(),out);
		}
		else{ //if its a leaf node
			out.writeBits(1, 1);
			out.writeBits(9, root.value());
		}
	}
	//finds the encoding and write's the encoding as a bit-sequence
	public void writeCompressedBits(BitInputStream in, String[] encodings, BitOutputStream out){
		if(in == null||out == null){ //checks input
			throw new NullPointerException("null in or out stream");}
		if(encodings == null){ //checks input
			throw new NullPointerException("null encodings array");}

		int bit = in.readBits(BITS_PER_WORD); //first bit
		while((bit!=-1)){ //loop through rest until hit end
			String encode = encodings[bit]; //gets the encoding for that bit
			out.writeBits(encode.length(), Integer.parseInt(encode,2));
			bit = in.readBits(BITS_PER_WORD); //moves onto next bit
		}

		//last bit
		String encode = encodings[PSEUDO_EOF];
		out.writeBits(encode.length(), Integer.parseInt(encode,2));
		in.reset();
	}

	//returns a HuffNode that's the root of the Huffman tree used for decompressing a file
	public HuffNode readTreeHeader(BitInputStream in){
		if(in == null){//checks input
			throw new NullPointerException("null in stream");}
		

		if(in.readBits(1) == 0){ //if its a internal node, make recursive calls to read the subtrees
			HuffNode left = readTreeHeader(in); //go through left
			HuffNode right = readTreeHeader(in); //go through right
			return new HuffNode(0,0, left, right);

		}
		else{ //if its a leaf node 
			return new HuffNode(in.readBits(9),1,null,null);
		}
	}
	//goes through and reads the compressed data 
	public void readCompressedBits(BitInputStream in, BitOutputStream out, HuffNode Hu){
		if(in ==null||out==null){ //checks input
			throw new NullPointerException("null in or out stream");
		}
		if(Hu == null){//checks input
			throw new NullPointerException("null HuffNode");
		}
		HuffNode node = Hu;
		int val = 0;
		while((val = in.readBits(1))!=-1){//while valid
			if(val == 0){ //if left, go left
				node = node.left();
			}
			if(val == 1){ //if right, go right
				node = node.right();
			}
			if(node.left()==null && node.right()==null){//if reached leaf
				if(node.value()==PSEUDO_EOF){ //checks if its the last signal
					return;
				}
				out.writeBits(8,node.value()); //write out the leaf value as 8 bit value
				node=Hu;
			}

		}
	}
}