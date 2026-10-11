package com.amethystclient.esp;

/** Categories of "fully grown" blocks that indicate a player kept the chunk loaded. */
public enum GrowthType {
	KELP_VINES("Kelp / Vines", "Kelp/Vines", 3, true),
	DRIPSTONE("Pointed Dripstone", "Dripstone", 3, true),
	AMETHYST("Amethyst Clusters", "Amethyst", 8, true),
	CROPS("Mature Crops", "Crops", 24, false);

	public final String label;
	public final String shortName;
	public final int defaultMinCount;
	public final boolean defaultEnabled;

	GrowthType(String label, String shortName, int defaultMinCount, boolean defaultEnabled) {
		this.label = label;
		this.shortName = shortName;
		this.defaultMinCount = defaultMinCount;
		this.defaultEnabled = defaultEnabled;
	}
}
