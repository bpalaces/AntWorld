package antworld.server;

import java.util.Random;

import antworld.common.AntAction;
import antworld.common.AntAction.AntActionType;
import antworld.common.AntData;
import antworld.common.AntType;
import antworld.common.Constants;
import antworld.common.FoodData;
import antworld.common.FoodType;
import antworld.common.LandType;
import antworld.common.NestNameEnum;
import antworld.common.TeamNameEnum;

public class Ant
{
  public static final int INVALID_ANT_ID = -7;
  private static Random random = Constants.random;
  private static int idCount = -1;

  
  public static AntData createAnt(AntType type, NestNameEnum nestName, TeamNameEnum teamName)
  {
    int id = getNewID();
    
    return new AntData(id, type, nestName, teamName);
  }
  
  
  public static synchronized int getNewID()
  {
    idCount++;
    return idCount;
  }
  
  public static int getAttackDamage(AntData ant)
  {
    int damage = 0;
    int dice = ant.antType.getAttackDiceD4();
    for (int i=0; i<dice; i++)
    {
      //add a uniformly distributed integer 1, 2, 3 or 4.
      damage += random.nextInt(4) + 1;
    }
    return damage;
  }
  

  
  public static boolean update(AntWorld world, AntData ant, AntAction action)
  {
    //System.out.println("Ant.update(): "+this+ ", " + action);
    if (!ant.underground)
    { if (random.nextDouble() < ant.antType.getAttritionDamageProbability()) ant.health--;
    }
    
    if (ant.health < 0) ant.alive = false;
    if (!ant.alive) return false;
    
    if (ant.ticksUntilNextAction > 0)
    { ant.ticksUntilNextAction--;
      return false;
    }
    
    
    if (action == null || action.type == AntActionType.STASIS) return false;
    
   
    //To avoid creating ants that cannot be created (not enough food) the birth action happens in Nest so nothing to do here.
    if (action.type == AntActionType.BIRTH) return true;

    if (action.type == AntActionType.EXIT_NEST)
    {
      if (!ant.underground) return false;
      Cell exitCell = world.getCell(action.x,action.y);
      //System.out.println("     ..... EXIT_NEST: exitCell="+exitCell + "("+ action.x+", " +action.y+")");
      
      if (exitCell == null) return false;
      if (!exitCell.isEmpty()) return false;
      if (exitCell.getNestName() != ant.nestName) return false;
      
      ant.gridX = action.x;
      ant.gridY = action.y;
      ant.underground = false;
      world.addAnt(ant);
      
      return true;
    }
    
    if (action.type == AntActionType.ENTER_NEST)
    {
      if (ant.underground) return false;
      if (world.getCell(ant.gridX, ant.gridY).getNestName() != ant.nestName) return false;

      ant.underground = true;
      world.removeAnt(ant);
      return true;
    }
   
    Nest myNest = world.getNest(ant.nestName);
    
    if (action.type == AntActionType.HEAL)
    { 
      if (ant.underground)
      { if (ant.health >= ant.antType.getMaxHealth()) return false;
        if (myNest.getFoodStockPile(FoodType.WATER) < 1) return false;
        myNest.consumeFood(FoodType.WATER, 1);
        ant.health += ant.antType.getHealPointsPerWaterUnit();
        return true;
      }
      
      if (ant.antType != AntType.MEDIC) return false;
      if (ant.carryType != FoodType.WATER) return false;
      if (ant.carryUnits <= 0) return false;
      AntData targetAnt = getTargetAnt(world, ant, action);
      if (targetAnt == null) return false;
      
      if (targetAnt.health >= targetAnt.antType.getMaxHealth()) return false;
      ant.carryUnits--;
      targetAnt.health += targetAnt.antType.getHealPointsPerWaterUnit(); 
      return true;
    }
      
    if (action.type == AntActionType.ATTACK)
    {
      AntData targetAnt = getTargetAnt(world, ant, action);
      if (targetAnt == null) return false;
      targetAnt.health -= getAttackDamage(ant);
      return true;
    }
    
    
    if (action.type == AntActionType.MOVE)
    {
      //Note: it is possible to move while underground.
      //  This would be done with in a nest to attack an ant on the surface.
      
      Cell cellTo = getTargetCell(world, ant, action);
      if (cellTo == null) return false;
      if (cellTo.getLandType() == LandType.WATER) return false;
      
      if (ant.underground)
      {
        ant.gridX = cellTo.getLocationX();
        ant.gridY = cellTo.getLocationY();
      }
      else 
      {
        if (!cellTo.isEmpty()) return false;
        Cell cellFrom = world.getCell(ant.gridX, ant.gridY);

        ant.ticksUntilNextAction = ant.antType.getBaseMovementTicksPerCell();
        if (cellTo.getHeight() > cellFrom.getHeight()) ant.ticksUntilNextAction *= ant.antType.getUpHillMultiplier();
        if (ant.carryUnits > 0)
        { ant.ticksUntilNextAction *= ant.antType.getHalfEncumbranceMultiplier();
          if (ant.carryUnits > ant.antType.getCarryCapacity()/2) ant.ticksUntilNextAction *= ant.antType.getHalfEncumbranceMultiplier();
        }
        world.moveAnt(ant, cellFrom, cellTo);
      }
      return true;
    }
    
    if (action.type == AntActionType.DROP)
    {
      if (ant.carryType == null) return false;
      if (ant.carryUnits <= 0) return false;
      if (action.quantity <= 0) return false;
      
      
      if (action.quantity > ant.carryUnits) action.quantity = ant.carryUnits;
      if (ant.underground)
      { 
    	  myNest.addFoodStockPile(ant.carryType, action.quantity);
        ant.carryUnits -= action.quantity;
        if (ant.carryUnits == 0) ant.carryType = null;
        //System.out.println("Ant.DROP "+ ant);
        
        return true;
      }
      
      
      Cell targetCell = getTargetCell(world, ant, action);
      if (targetCell == null) return false;
      if (targetCell.getLandType() == LandType.NEST)
      {
    	//System.out.println("Ant.DROP-- landtype: nest = " + targetCell.getNest().nestName 
    	//		+ ", food[" + ant.carryType + "] drop="+action.quantity + 
    	//		"stockPile="+targetCell.getNest().getFoodStockPile(ant.carryType));
        targetCell.getNest().addFoodStockPile(ant.carryType, action.quantity);
        //System.out.println("       After drop: stockPile="+targetCell.getNest().getFoodStockPile(ant.carryType));
        
        //System.out.println("        targetCell.getNest()="+System.identityHashCode(targetCell.getNest()));
        //System.out.println("Ant.DROP in Nest"+ targetCell.getNestName() + "["+ant.carryType+"]="+ant.carryUnits );
        ant.carryUnits -= action.quantity;
        if (ant.carryUnits == 0) ant.carryType = null;
        return true;
      }
      if (!targetCell.isEmpty()) return false;
      
      int x = targetCell.getLocationX();
      int y = targetCell.getLocationY();  
      FoodData droppedFood = new FoodData(ant.carryType, x, y, ant.carryUnits);
      ant.carryUnits -= action.quantity;
      if (ant.carryUnits == 0) ant.carryType = null;
      world.addFood(null, droppedFood);
      return true;
    }
    
    
    if (action.type == AntActionType.PICKUP)
    {
      if (action.quantity <=0) return false;
      //if (action.quantity > antType.getCarryCapacity()) action.quantity = antType.getCarryCapacity();
      Cell targetCell = getTargetCell(world, ant, action);
      if (targetCell == null) return false;
      
      if (targetCell.getLandType() == LandType.WATER)
      { 
        if ((ant.carryUnits > 0) && (ant.carryType!=FoodType.WATER)) return false;
        ant.carryType = FoodType.WATER;
      }
      else
      {  
        FoodData groundFood = targetCell.getFood();
        if (groundFood == null) return false;
        
        if ((ant.carryUnits > 0) && (ant.carryType!=groundFood.foodType)) return false;
        if (action.quantity > groundFood.getCount()) action.quantity = groundFood.getCount();
        ant.carryType = groundFood.foodType;
      }
      if (ant.carryUnits + action.quantity > ant.antType.getCarryCapacity()) action.quantity = ant.antType.getCarryCapacity() - ant.carryUnits;
      ant.carryUnits += action.quantity;
      
      if (targetCell.getLandType() != LandType.WATER)
      {
        GameObject gameObj = targetCell.getGameObject();
        
        gameObj.setFoodCount(world, ant.nestName, gameObj.getFoodCount() - action.quantity);
        //System.out.println("Ant.update("+ant.nestName+") pickup " + gameObj.food.foodType + "[" + action.quantity + "] remaining = " + gameObj.getFoodCount());
        if (gameObj.getFoodCount() <= 0) world.removeGameObj(gameObj);
      }
      
      
      return true;
    }
    
    return false;
  }
  
  

  
  private static Cell getTargetCell(AntWorld world, AntData ant, AntAction action)
  {
    if (action.direction == null) return null;
    int targetX = ant.gridX + action.direction.deltaX();
    int targetY = ant.gridY + action.direction.deltaY();
    return world.getCell(targetX, targetY);
  }

  
  
  private static AntData getTargetAnt(AntWorld world, AntData ant, AntAction action)
  {
    Cell targetCell = getTargetCell(world, ant, action);
    if (targetCell == null) return null;
    return targetCell.getAnt();
  }
}
