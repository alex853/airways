package net.simforge.airways.model.geo;

@Deprecated
public interface City /*extends BaseEntity, EventLog.Loggable*/ {

    Country getCountry();

    void setCountry(Country country);

    String getName();

    void setName(String name);

    Double getLatitude();

    void setLatitude(Double latitude);

    Double getLongitude();

    void setLongitude(Double longitude);

    Integer getPopulation();

    void setPopulation(Integer population);

    Integer getDataset();

    void setDataset(Integer dataset);

}
