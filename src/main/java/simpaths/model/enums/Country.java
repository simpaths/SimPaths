package simpaths.model.enums;

public enum Country {
	
	UK("United Kingdom", 15);			//United Kingdom of Great Britain and Northern Ireland
	
	//UKMOD country codes for all EU countries
//	Country	dct
//	AT	1
//	BE	2
//	DK	3
//	FI	4
//	FR	5
//	DE	6
//	EL	7
//	IE	8
//	IT	9
//	LU	10
//	NL	11
//	PT	12
//	ES	13
//	SE	14
//	UK	15
//	EE	16
//	HU	17
//	PL	18
//	SI	19
//	BG	20
//	CZ	21
//	CY	22
//	LV	23
//	LT	24
//	MT	25
//	RO	26
//	SK	27

	
	private final String countryName;
	private final int ukmodCountryCode;		//The number that UKMOD uses to represent a country (the variable 'dct')
	
	private Country(String countryName, int ukmodCountryCode)
    {
        this.countryName = countryName;
        this.ukmodCountryCode = ukmodCountryCode;
    }

    public String getCountryName()
    {
        return countryName;
    }
    
    public static Country getCountryFromNameString(String country) {
    	for(Country c: Country.values()) {
    		if(country.equals(c.getCountryName())) {
    			return c;
    		}
    	}
    	throw new IllegalArgumentException("ERROR - no such country name!");
    }
    
    /**
     * @return the number representing the UKMOD variable dct, the country variable in UKMOD
     */
    public int getUKMODCountryCode() {
        return ukmodCountryCode;
    }
    
}
