package net.simforge.airways.model.geo;

@Deprecated
public interface Airport /*extends BaseEntity, EventLog.Loggable*/ {

    String getIata();

    void setIata(String iata);

    String getIcao();

    void setIcao(String icao);

    String getName();

    void setName(String name);

    Double getLatitude();

    void setLatitude(Double latitude);

    Double getLongitude();

    void setLongitude(Double longitude);

    Integer getDataset();

    void setDataset(Integer dataset);

}
