import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Provides a method to walkes a ZFIN file.
 * Calls {@link ZFINVisitor#visit(ZFINEntry)} for each encountered entry.
 * 
 *  0 ZDB-GENE_id 
 *  1 Entrez Zebrafish GeneID 
 *  2 Entrez Human GeneID 
 *  3 zebrafish gene symbol 
 *  4 E1a 
 *  5 E1b 
 *  6 E2a 
 *  7 E2b 
 *  8 quality 
 *  9 tag
 * 
 * example:
 * 0 ZDB-GENE-040426-1716
 * 1 393723
 * 2 6223
 * 3 rps19
 * 4 ZFA:0000108
 * 5 
 * 6 
 * 7 
 * 8 PATO:0000462
 * 9 abnormal
 * 
 * @author Sebastian Bauer
 */
public class ZFINWalker
{
	private ZFINWalker() {};
	
	static int COLUMN_ZFIN_GENE_ID = 0;

	static int COLUMN_TERM1_SUPERTERM_ID = 6;
	static int COLUMN_TERM1_SUPERTERM_NAME = 7;
	static int COLUMN_TERM1_SUBTERM_ID = 8;
	static int COLUMN_TERM1_SUBTERM_NAME = 9;	
	static int COLUMN_TERM2_SUPERTERM_ID = 13;
	static int COLUMN_TERM2_SUPERTERM_NAME = 14;
	static int COLUMN_TERM2_SUBTERM_ID = 15;
	static int COLUMN_TERM2_SUBTERM_NAME = 16;

	static int COLUMN_PATO_ID = 10;
	static int COLUMN_PATO_NAME = 11;
	static int COLUMN_PATO_ABNORMAL_NORMAL = 12;
	
	static public void walk(InputStream input, ZFINVisitor visitor) throws IOException
	{
		BufferedReader in = new BufferedReader(new InputStreamReader(input));
		String line;
		while ((line = in.readLine()) != null)
		{
			ZFINEntry entry = new ZFINEntry();
			
			String [] sp = line.split("\\t");

			entry.geneZfinID = sp[COLUMN_ZFIN_GENE_ID];

			entry.entity1SupertermId = sp[COLUMN_TERM1_SUPERTERM_ID];
			entry.entity1SupertermName = sp[COLUMN_TERM1_SUPERTERM_NAME];
			entry.entity1SubtermId = sp[COLUMN_TERM1_SUBTERM_ID];
			entry.entity1SubtermName = sp[COLUMN_TERM1_SUBTERM_NAME];
			
			entry.entity2SupertermId = sp[COLUMN_TERM2_SUPERTERM_ID];
			entry.entity2SupertermName = sp[COLUMN_TERM2_SUPERTERM_NAME];
			entry.entity2SubtermId = sp[COLUMN_TERM2_SUBTERM_ID];
			entry.entity2SubtermName = sp[COLUMN_TERM2_SUBTERM_NAME];

			entry.patoID = sp[COLUMN_PATO_ID];
			entry.patoName = sp[COLUMN_PATO_NAME];
			entry.isAbnormal = sp[COLUMN_PATO_ABNORMAL_NORMAL].equalsIgnoreCase("abnormal");
			visitor.visit(entry);
		}
	}
}
