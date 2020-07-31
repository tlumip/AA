-- view to compare commodity zutilities by components, between scenarios.
drop view if exists output.commodity_utility_diff;
create or replace view output.commodity_utility_diff as
select s1.scenario as scenario_base,
  s2.scenario as scenario_alt,
  s1.year_run,
  s1.commodity,
  s1.buyingorselling,
  s1.zone,
  s1.quantity as quantity_base,
  s2.quantity as quantity_alt,
  s2.quantity - s1.quantity as quantity_diff,
  (s2.zutility - s1.zutility) as zutility_diff,
  (s2.pricecomponent - s1.pricecomponent) as price_utility_diff,
  (s2.variationcomponent - s1.variationcomponent) as variation_utility_diff,
  (s2.sizecomponent - s1.sizecomponent) as size_utility_diff,
  (
  case when s2.transportcomponent1 is null then 0 else s2.transportcomponent1 end +
  case when s2.transportcomponent2 is null then 0 else s2.transportcomponent2 end +
  case when s2.transportcomponent3 is null then 0 else s2.transportcomponent3 end +
  case when s2.transportcomponent4 is null then 0 else s2.transportcomponent4 end
  ) - (
  case when s1.transportcomponent1 is null then 0 else s1.transportcomponent1 end +
  case when s1.transportcomponent2 is null then 0 else s1.transportcomponent2 end +
  case when s1.transportcomponent3 is null then 0 else s1.transportcomponent3 end +
  case when s1.transportcomponent4 is null then 0 else s1.transportcomponent4 end
  ) as transport_utility_diff
  FROM output.all_commodity_zutilities s1, output.all_commodity_zutilities s2
  WHERE s1.year_run = s2.year_run AND 
  s1.commodity::text = s2.commodity::text AND 
  s1.buyingorselling::text = s2.buyingorselling::text 
  AND s1.zone = s2.zone;

select * from output.commodity_utility_diff
where scenario_alt = 'W16F' and scenario_base = 'W12F' 
and quantity_base >0 and quantity_alt >0
order by transport_utility_diff
limit 100;

-- DROP VIEW output.activity_commodity__roh_w12_w16;

-- View: output.activity_commodity_rule_of_half

-- DROP VIEW output.activity_commodity_rule_of_half;

CREATE OR REPLACE VIEW output.activity_commodity_rule_of_half AS 
 SELECT s1.scenario AS scenario_base, 
	s2.scenario AS scenario_alt, 
	s1.year_run, 
	s1.activity, 
	s1.zonenumber, 
	s1.commodity, 
	s1.moru, 
	s1.utility AS base_utility, 
	s2.utility AS alt_utlity, 
	s1.amount AS base_amount, 
	s2.amount AS alt_amount, 
        CASE
            WHEN s1.moru::text = 'M'::text THEN (s2.utility - s1.utility) * (s1.amount + s2.amount) / 2::double precision
            WHEN s1.moru::text = 'U'::text THEN (- (s2.utility - s1.utility)) * (s1.amount + s2.amount) / 2::double precision
            ELSE NULL::double precision
        END AS rule_of_half
   FROM output.all_zonalmakeuse s1, 
	output.all_zonalmakeuse s2
  WHERE s1.year_run = s2.year_run 
	AND s1.commodity = s2.commodity 
	AND s1.activity = s2.activity 
	AND s1.moru::text = s2.moru::text 
	AND s1.zonenumber = s2.zonenumber 
	AND (s1.amount <> 0::double precision OR s2.amount <> 0::double precision);


CREATE OR REPLACE VIEW output.activity_commodity_roh_components
as
 SELECT ah.scenario_base, 
	 ah.scenario_alt,
	 ah.year_run,
	 ah.activity,
	 ah.zonenumber, -- eventually sum by zone number
	 ah.commodity,
	 ah.moru,
	 ah.base_amount,
	 ah.alt_amount,
	 ah.rule_of_half,
	 (ah.base_amount+ah.alt_amount)/2*cd.zutility_diff as recalc_roh, -- delete this, just a check
	 (ah.base_amount+ah.alt_amount)/2*cd.price_utility_diff as price_ben,
	 (ah.base_amount+ah.alt_amount)/2*(cd.size_utility_diff + cd.variation_utility_diff) as accessibility_ben,
	 (ah.base_amount+ah.alt_amount)/2*cd.transport_utility_diff as transport_ben
   from output.activity_commodity_rule_of_half ah,
	output.commodity_utility_diff cd,
	output.commodity_numbers cn
   where
	ah.scenario_base = cd.scenario_base
	and ah.scenario_alt = cd.scenario_alt
	and ah.year_run = cd.year_run
	and ah.zonenumber = cd.zone
	and ah.commodity = cn.commoditynumber and cd.commodity = cn.commodity
	and ((ah.moru = 'M' and cd.buyingorselling = 'S') or
	      (ah.moru = 'U' and cd.buyingorselling = 'B'))

	      

	 
select an.activity, cn.commodity, moru, sum(rule_of_half) as rule_of_half, 
sum(price_ben) as price_ben,
sum(accessibility_ben) as accessibility_ben,
sum(transport_ben) as transport_ben
 from output.activity_commodity_roh_components rh
   join output.activity_numbers an on rh.activity = an.activitynumber
   join output.commodity_numbers cn on rh.commodity = cn.commoditynumber
where scenario_alt = 'W16F' and scenario_base = 'W12F' 
and year_run = 2007
group by an.activity, cn.commodity, moru
order by activity, commodity, moru;

create view output.benefit_components_activity_commodity
as 
select scenario_base, scenario_alt, year_run, an.activity, cn.commodity, moru, sum(rule_of_half) as rule_of_half, 
sum(price_ben) as price_ben,
sum(accessibility_ben) as accessibility_ben,
sum(transport_ben) as transport_ben
 from output.activity_commodity_roh_components rh
   join output.activity_numbers an on rh.activity = an.activitynumber
   join output.commodity_numbers cn on rh.commodity = cn.commoditynumber
group by scenario_base, scenario_alt, year_run, an.activity, cn.commodity, moru
order by scenario_base, scenario_alt, year_run, activity, commodity, moru;


  
