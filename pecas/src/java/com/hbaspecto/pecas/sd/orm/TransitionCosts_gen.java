package com.hbaspecto.pecas.sd.orm;
import simpleorm.dataset.*;
import simpleorm.utils.*;
import simpleorm.sessionjdbc.SSessionJdbc;
import java.math.BigDecimal;
import java.util.Date;

import com.hbaspecto.pecas.sd.SpaceTypesI;

/**	Base class of table transition_costs.<br>
*Do not edit as will be regenerated by running SimpleORMGenerator
*Generated on Fri Sep 25 16:13:29 MDT 2009
***/
abstract class TransitionCosts_gen extends SRecordInstance implements java.io.Serializable {

   public static final SRecordMeta <TransitionCosts> meta = new SRecordMeta<TransitionCosts>(TransitionCosts.class, "transition_costs");

//Columns in table
   public static final SFieldInteger CostScheduleId =
      new SFieldInteger(meta, "cost_schedule_id",
         new SFieldFlags[] { SFieldFlags.SPRIMARY_KEY, SFieldFlags.SMANDATORY });

   public static final SFieldInteger SpaceTypeId =
      new SFieldInteger(meta, "space_type_id",
         new SFieldFlags[] { SFieldFlags.SPRIMARY_KEY, SFieldFlags.SMANDATORY });

   public static final SFieldDouble DemolitionCost =
      new SFieldDouble(meta, "demolition_cost");

   public static final SFieldDouble RenovationCost =
      new SFieldDouble(meta, "renovation_cost");

   public static final SFieldDouble AdditionCost =
      new SFieldDouble(meta, "addition_cost");

   public static final SFieldDouble ConstructionCost =
      new SFieldDouble(meta, "construction_cost");
   
   public static final SFieldDouble RenovationDerelictCost =
	      new SFieldDouble(meta, "renovation_derelict_cost");

//Column getters and setters
   public int get_CostScheduleId(){ return getInt(CostScheduleId);}
   public void set_CostScheduleId( int value){setInt( CostScheduleId,value);}

   public int get_SpaceTypeId(){ return getInt(SpaceTypeId);}
   public void set_SpaceTypeId( int value){setInt( SpaceTypeId,value);}

   public double get_DemolitionCost(){ return getDouble(DemolitionCost);}
   public void set_DemolitionCost( double value){setDouble( DemolitionCost,value);}

   public double get_RenovationCost(){ return getDouble(RenovationCost);}
   public void set_RenovationCost( double value){setDouble( RenovationCost,value);}
   
   public double get_RenovationDerelictCost(){ return getDouble(RenovationDerelictCost);}
   public void set_RenovationDerelictCost( double value){setDouble( RenovationDerelictCost,value);}

   public double get_AdditionCost(){ return getDouble(AdditionCost);}
   public void set_AdditionCost( double value){setDouble( AdditionCost,value);}

   public double get_ConstructionCost(){ return getDouble(ConstructionCost);}
   public void set_ConstructionCost( double value){setDouble( ConstructionCost,value);}

//Foreign key getters and setters
   public SpaceTypesI get_SPACE_TYPES_I(SSessionJdbc ses){
     try{
/** Old code: 
        return SpaceTypesI.findOrCreate(get_SpaceTypeId());
New code below :**/
        return ses.findOrCreate(SpaceTypesI.meta,new Object[]{ 
        	get_SpaceTypeId(),
 });
     } catch (SException e) {
        if (e.getMessage().indexOf("Null Primary key") > 0) {
          return null;
        }
        throw e;
     }
   }
   public void set_SPACE_TYPES_I( SpaceTypesI value){
      set_SpaceTypeId( value.get_SpaceTypeId());
   }

   public TransitionCostCodes get_TRANSITION_COST_CODES(SSessionJdbc ses){
     try{
/** Old code: 
        return TransitionCostCodes.findOrCreate(get_CostScheduleId());
New code below :**/
        return ses.findOrCreate(TransitionCostCodes.meta,new Object[]{ 
        	get_CostScheduleId(),
 });
     } catch (SException e) {
        if (e.getMessage().indexOf("Null Primary key") > 0) {
          return null;
        }
        throw e;
     }
   }
   public void set_TRANSITION_COST_CODES( TransitionCostCodes value){
      set_CostScheduleId( value.get_CostScheduleId());
   }

//Find and create
   public static TransitionCosts findOrCreate( SSessionJdbc ses ,int _CostScheduleId, int _SpaceTypeId ){
      return ses.findOrCreate(meta, new Object[] {new Integer( _CostScheduleId), new Integer( _SpaceTypeId)});
   }
   public static TransitionCosts findOrCreate( SSessionJdbc ses,SpaceTypesI _ref, int _CostScheduleId){
      return findOrCreate( ses, _CostScheduleId, _ref.get_SpaceTypeId());
   }

   public static TransitionCosts findOrCreate( SSessionJdbc ses,TransitionCostCodes _ref, int _SpaceTypeId){
      return findOrCreate( ses, _ref.get_CostScheduleId(), _SpaceTypeId);
   }

   public SRecordMeta <TransitionCosts> getMeta() {
       return meta;
   }
}
