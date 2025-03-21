package com.grishin.apartment.checker.dto;

import lombok.Data;

import java.util.List;

@Data
public class AptDTO {
    private String buildingNumber;
    private String communityIDAEM;
    private String communityMarketingName;
    private String featuredAmenity;
    private int floorplanBath;
    private int floorplanBed;
    private String floorplanCRMID;
    private int floorplanDeposit;
    private String floorplanID;
    private String floorplanName;
    private String floorplanPath;
    private int floorplanSqFt;
    private String floorplanUniqueID;
    private String propertyAddress;
    private int propertyID;
    private String propertyZip;
    private List<String> unitAmenities;
    private String unitCRMID;
    private LeaseTermDTO unitEarliestAvailable;
    private int unitFloor;
    private boolean unitHasDiscount;
    private String unitID;
    private boolean unitIsStudio;
    private List<LeaseTermDTO> unitLeasePrice;
    private String unitMarketingName;
    private int unitSqFt;
    private LeaseTermDTO unitStartingPrice;
    private String unitTypeCode;
    private String unitTypeName;
    private String objectID;
}
