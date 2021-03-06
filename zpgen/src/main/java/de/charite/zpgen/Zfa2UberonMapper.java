package de.charite.zpgen;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import com.google.common.collect.ImmutableSetMultimap;

/**
 * Create a mapping of ZFA-class-IDs to UBERON-class-IDs using the UBERON obo file. The UBERON obo file contains xref-tags for the
 * ZFA-classes.
 * 
 * 
 * @author sebastiankohler
 *
 */
public class Zfa2UberonMapper {

	/**
	 * Using multimap because some ZFA-classes are xref'd by multiple UBERON classes
	 */
	private ImmutableSetMultimap<String, String> zfa2uberonIm = null;

	/**
	 * @param uberonOboFilePath
	 */
	public Zfa2UberonMapper(String uberonOboFilePath) {
		try {

			BufferedReader in = new BufferedReader(new FileReader(uberonOboFilePath));
			String line = null;
			String currentUberonId = null;
			String currentZfaId = null;
			// using multimap because some ZFA-classes are used as xref by multiple UBERON classes
			ImmutableSetMultimap.Builder<String, String> builder = ImmutableSetMultimap.builder();
			while ((line = in.readLine()) != null) {
				line = line.trim();

				// use empty lines as indicator for new element
				if (line.equals("")) {
					if (currentUberonId != null && currentZfaId != null) {
						builder.put(currentZfaId, currentUberonId);
					}
					currentUberonId = null;
					currentZfaId = null;
				}
				if (line.startsWith("id: UBERON:")) {
					currentUberonId = line.replace("id: ", "");
				}
				if (line.startsWith("xref: ZFA:")) {
					currentZfaId = line.replace("xref: ", "");
				}
			}
			in.close();

			zfa2uberonIm = builder.build();

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public ImmutableSetMultimap<String, String> getZfa2UberonMapping() {
		return zfa2uberonIm;
	}

}
