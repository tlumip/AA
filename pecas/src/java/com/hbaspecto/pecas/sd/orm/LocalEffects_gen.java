package com.hbaspecto.pecas.sd.orm;
import simpleorm.dataset.*;
import simpleorm.utils.*;
import simpleorm.sessionjdbc.SSessionJdbc;
import java.math.BigDecimal;
import java.util.Date;

/**	Base class of table local_effects.<br>
*Do not edit as will be regenerated by running SimpleORMGenerator
*Generated on Fri Sep 25 16:13:29 MDT 2009
***/
abstract class LocalEffects_gen extends SRecordInstance implements java.io.Serializable {

   public static final SRecordMeta <LocalEffects> meta = new SRecordMeta<LocalEffects>(LocalEffects.class, "local_effects");

//Columns in table
   public static final SFieldInteger LocalEffectId =
      new SFieldInteger(meta, "local_effect_id",
         new SFieldFlags[] { SFieldFlags.SPRIMARY_KEY, SFieldFlags.SMANDATORY });

   public static final SFieldString LocalEffectName =
      new SFieldString(meta, "local_effect_name", 2147483647);
   
   public static final SFieldBoolean RentLocalEffect =
	   new SFieldBooleanBit(meta, "rent_local_effect");

   public static final SFieldBoolean CostLocalEffect =
	   new SFieldBooleanBit(meta, "cost_local_effect");

   //Column getters and setters
   public int get_LocalEffectId(){ return getInt(LocalEffectId);}
   public void set_LocalEffectId( int value){setInt( LocalEffectId,value);}

   public String get_LocalEffectName(){ return getString(LocalEffectName);}
   public void set_LocalEffectName( String value){setString( LocalEffectName,value);}
   

	public boolean isRentLocalEffect() {return getBoolean(RentLocalEffect);}
	public void set_RentLocalEffect(boolean value){setBoolean(RentLocalEffect,value);}


	public boolean isCostLocalEffect() {return getBoolean(CostLocalEffect);}
	public void set_CostLocalEffect(boolean value){setBoolean(CostLocalEffect,value);}

	//Find and create
   public static LocalEffects findOrCreate( SSessionJdbc ses ,int _LocalEffectId ){
      return ses.findOrCreate(meta, new Object[] {new Integer( _LocalEffectId)});
   }
   public SRecordMeta <LocalEffects> getMeta() {
       return meta;
   }
}
