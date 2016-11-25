package antworld.common;

public enum TeamNameEnum
{
  EMPTY,
  NEARLY_BRAINLESS_BOTS, 
  RANDOM_WALKERS,
  JudgemAnt,        //Nick & Demitri
  Allen_Brendan,
  Arthur_Phil,
  Atle_Caleb,
  Connor_Rob,
  Daniel_Corey,
  Ederin_Dominic,
  Forrest_Michael,
  Hector_Haijin,
  Jaehee_Edgar,
  John_Mauricio,
  Josh_Anton,
  Katrina_William,
  Kenneth_Germaine,
  Kevin_Sahba,
  Michael_Joaquin,
  Nathan_Nicholas,
  Robin_Alex,
  Sam_Dominic,
  Sarah_Beatriz,
  Kim_Javier,
  Linh_Dustin;

  public static TeamNameEnum getTeamByString(String name)
  {
    for(TeamNameEnum team : values())
    {
      if( name.equals(team.name()))
      {
        return team;
      }
    }
    return null;
  }
}
