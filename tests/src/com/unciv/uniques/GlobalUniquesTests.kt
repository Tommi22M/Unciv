//  Taken from https://github.com/TomGrill/gdx-testing
package com.unciv.uniques

import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.logic.map.RoadStatus
import com.unciv.models.ruleset.BeliefType
import com.unciv.models.stats.Stats
import com.unciv.testing.GdxTestRunner
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
class GlobalUniquesTests {

    private lateinit var game: TestGame

    @Before
    fun initTheWorld() {
        game = TestGame()
    }

    // region stat uniques

    @Test
    fun stats() {
        val civInfo = game.addCiv()
        val tile = game.setTileFeatures(Vector2(0f,0f), Constants.desert)
        val cityInfo = game.addCity(civInfo, tile, true)
        val buildingName = game.createBuilding("[+1 Food]").name

        cityInfo.cityConstructions.addBuilding(buildingName)
        cityInfo.cityStats.update()
        Assert.assertTrue(cityInfo.cityStats.finalStatList["Buildings"]!!.equals(Stats(food=1f)))
    }

    @Test
    fun statsPerCity() {
        val civInfo = game.addCiv()
        val tile = game.setTileFeatures(Vector2(0f,0f), Constants.desert)
        val cityInfo = game.addCity(civInfo, tile, true)
        val buildingName = game.createBuilding("[+1 Production] [in this city]").name

        cityInfo.cityConstructions.addBuilding(buildingName)
        cityInfo.cityStats.update()
        Assert.assertTrue(cityInfo.cityStats.finalStatList["Buildings"]!!.equals(Stats(production=1f)))
    }

    @Test
    fun statsPerSpecialist() {
        val civInfo = game.addCiv()
        val tile = game.setTileFeatures(Vector2(0f,0f), Constants.desert)
        val cityInfo = game.addCity(civInfo, tile, true, initialPopulation = 2)
        val building = game.createBuilding("[+3 Gold] from every specialist [in this city]")
        val specialistName = game.addEmptySpecialist()
        building.specialistSlots.add(specialistName, 2)
        cityInfo.population.specialistAllocations[specialistName] = 2

        cityInfo.cityConstructions.addBuilding(building.name)
        cityInfo.cityStats.update()
        Assert.assertTrue(cityInfo.cityStats.finalStatList["Specialists"]!!.equals(Stats(gold=6f)))
    }

    @Test
    fun statsPerPopulation() {
        val civInfo = game.addCiv()
        val tile = game.setTileFeatures(Vector2(0f,0f), Constants.desert)
        val cityInfo = game.addCity(civInfo, tile, true, initialPopulation = 4)
        val building = game.createBuilding("[+3 Gold] per [2] population [in this city]")

        cityInfo.cityConstructions.addBuilding(building.name)
        cityInfo.cityStats.update()
        Assert.assertTrue(cityInfo.cityStats.finalStatList["Buildings"]!!.gold == 6f)
    }

    @Test
    fun statsPerXPopulation() {
        val civInfo = game.addCiv()
        val tile = game.setTileFeatures(Vector2(0f,0f), Constants.desert)
        val cityInfo = game.addCity(civInfo, tile, true, initialPopulation = 2)
        val building = game.createBuilding("[+3 Gold] in cities with [3] or more population")

        cityInfo.cityConstructions.addBuilding(building.name)

        cityInfo.cityStats.update()
        Assert.assertTrue(cityInfo.cityStats.finalStatList["Buildings"]!!.gold == 0f)
        cityInfo.population.setPopulation(5)
        cityInfo.cityStats.update()
        Assert.assertTrue(cityInfo.cityStats.finalStatList["Buildings"]!!.gold == 3f)
    }

    @Test
    fun statsFromCitiesOnSpecificTiles() {
        val civInfo = game.addCiv()
        val tile = game.setTileFeatures(Vector2(0f,0f), Constants.desert)
        val cityInfo = game.addCity(civInfo, tile, true)
        val building = game.createBuilding("[+3 Gold] in cities on [${Constants.desert}] tiles")
        cityInfo.cityConstructions.addBuilding(building.name)

        cityInfo.cityStats.update()
        Assert.assertTrue(cityInfo.cityStats.finalStatList["Buildings"]!!.gold == 3f)
        tile.baseTerrain = Constants.grassland
        cityInfo.cityStats.update()
        Assert.assertTrue(cityInfo.cityStats.finalStatList["Buildings"]!!.gold == 0f)
    }

    @Test
    fun statsFromTiles() {
        game.makeHexagonalMap(2)
        val civInfo = game.addCiv()
        val tile = game.setTileFeatures(Vector2(0f,0f), Constants.desert)
        val cityInfo = game.addCity(civInfo, tile, true)
        val building = game.createBuilding("[+4 Gold] from [${Constants.grassland}] tiles [in all cities]")
        cityInfo.cityConstructions.addBuilding(building.name)

        val tile2 = game.setTileFeatures(Vector2(0f,1f), Constants.grassland)
        Assert.assertTrue(tile2.getTileStats(cityInfo, civInfo).gold == 4f)
    }

    @Test
    fun statsFromTilesWithout() {
        game.makeHexagonalMap(3)
        val civInfo = game.addCiv()
        val tile = game.setTileFeatures(Vector2(0f,0f), Constants.desert)
        val cityInfo = game.addCity(civInfo, tile, true)
        val building = game.createBuilding("[+4 Gold] from [${Constants.grassland}] tiles without [${Constants.forest}] [in this city]")
        cityInfo.cityConstructions.addBuilding(building.name)

        val tile2 = game.setTileFeatures(Vector2(0f,1f), Constants.grassland)
        game.addTileToCity(cityInfo, tile2)
        Assert.assertTrue(tile2.getTileStats(cityInfo, civInfo).gold == 4f)

        val tile3 = game.setTileFeatures(Vector2(0f, 2f), Constants.grassland, listOf(Constants.forest))
        game.addTileToCity(cityInfo, tile3)
        Assert.assertFalse(tile3.getTileStats(cityInfo, civInfo).gold == 4f)
    }

    @Test
    fun statsFromObject() {
        game.makeHexagonalMap(1)
        val civInfo = game.addCiv()
        val tile = game.setTileFeatures(Vector2(0f,0f), Constants.desert)
        val cityInfo = game.addCity(civInfo, tile, true, initialPopulation = 2)
        val specialist = game.addEmptySpecialist()
        val building = game.createBuilding("[+3 Faith] from every [${specialist}]")

        cityInfo.cityConstructions.addBuilding(building.name)
        cityInfo.population.specialistAllocations[specialist] = 2

        cityInfo.cityStats.update()
        Assert.assertTrue(cityInfo.cityStats.finalStatList["Specialists"]!!.faith == 6f)

        cityInfo.cityConstructions.removeBuilding(building.name)
        val building2 = game.createBuilding("[+3 Faith] from every [${Constants.grassland}]")
        cityInfo.cityConstructions.addBuilding(building2.name)

        val tile2 = game.setTileFeatures(Vector2(0f,1f), Constants.grassland)
        Assert.assertTrue(tile2.getTileStats(cityInfo, civInfo).faith == 3f)

        cityInfo.cityConstructions.removeBuilding(building2.name)

        val emptyBuilding = game.createBuilding()

        val building3 = game.createBuilding("[+3 Faith] from every [${emptyBuilding.name}]")
        cityInfo.cityConstructions.addBuilding(emptyBuilding.name)
        cityInfo.cityConstructions.addBuilding(building3.name)
        cityInfo.cityStats.update()
        Assert.assertTrue(cityInfo.cityStats.finalStatList["Buildings"]!!.faith == 3f)
    }

    @Test
    fun statsFromTradeRoute() {
        game.makeHexagonalMap(3)
        val civInfo = game.addCiv("[+30 Science] from each Trade Route")
        civInfo.tech.addTechnology("The Wheel") // Required to form trade routes
        val tile1 = game.setTileFeatures(Vector2(0f,0f), Constants.desert)
        val tile2 = game.setTileFeatures(Vector2(0f,2f), Constants.desert)
        tile1.roadStatus = RoadStatus.Road
        tile2.roadStatus = RoadStatus.Road
        @Suppress("UNUSED_VARIABLE")
        val city1 = game.addCity(civInfo, tile1)
        val city2 = game.addCity(civInfo, tile2)
        val inBetweenTile = game.setTileFeatures(Vector2(0f, 1f), Constants.desert)
        inBetweenTile.roadStatus = RoadStatus.Road
        civInfo.transients().updateCitiesConnectedToCapital()
        city2.cityStats.update()

        Assert.assertTrue(city2.cityStats.finalStatList["Trade routes"]!!.science == 30f)
    }

    @Test
    fun statsFromGlobalCitiesFollowingReligion() {
        val civ1 = game.addCiv()
        val religion = game.addReligion(civ1)
        val belief = game.createBelief(BeliefType.Founder, "[+30 Science] for each global city following this religion")
        religion.founderBeliefs.add(belief.name)
        val civ2 = game.addCiv()
        val tile = game.getTile(Vector2(0f,0f))
        val cityOfCiv2 = game.addCity(civ2, tile, initialPopulation = 1) // Need someone to be converted
        cityOfCiv2.religion.addPressure(religion.name, 1000)

        Assert.assertTrue(cityOfCiv2.religion.getMajorityReligionName() == religion.name)

        civ1.updateStatsForNextTurn()

        Assert.assertTrue(civ1.statsForNextTurn.science == 30f)
    }

    @Test
    fun happinessFromGlobalCitiesFollowingReligion() {
        val civ1 = game.addCiv()
        val religion = game.addReligion(civ1)
        val belief = game.createBelief(BeliefType.Founder, "[+42 Happiness] for each global city following this religion")
        religion.founderBeliefs.add(belief.name)
        val civ2 = game.addCiv()
        val tile = game.getTile(Vector2(0f,0f))
        val cityOfCiv2 = game.addCity(civ2, tile, initialPopulation = 1) // Need someone to be converted
        cityOfCiv2.religion.addPressure(religion.name, 1000)

        civ1.updateStatsForNextTurn()

        val baseHappiness = civ1.getDifficulty().baseHappiness
        // Since civ1 has no cities, there are no other happiness sources
        Assert.assertTrue(civ1.happinessForNextTurn == baseHappiness + 42)
    }

    @Test
    fun statsFromGlobalFollowers() {
        val civ1 = game.addCiv()
        val religion = game.addReligion(civ1)
        val belief = game.createBelief(BeliefType.Founder, "[+30 Science] from every [3] global followers [in all cities]")
        religion.founderBeliefs.add(belief.name)
        val civ2 = game.addCiv()
        val tile = game.getTile(Vector2(0f,0f))
        val cityOfCiv2 = game.addCity(civ2, tile, initialPopulation = 9) // Need people to be converted
        cityOfCiv2.religion.addPressure(religion.name, 1000000000) // To completely overwhelm the default atheism in a city

        civ1.updateStatsForNextTurn()

        Assert.assertTrue(civ1.statsForNextTurn.science == 90f)
    }

    // endregion

    // region stat percentage bonus providing uniques

    @Test
    fun statPercentBonus() {
        val civ = game.addCiv()
        val tile = game.getTile(Vector2(0f, 0f))
        val city = game.addCity(civ, tile, true)
        val building = game.createBuilding("[+10 Science]", "[+200]% [Science]")
        city.cityConstructions.addBuilding(building.name)
        city.cityStats.update()

        Assert.assertTrue(city.cityStats.finalStatList["Buildings"]!!.science == 30f)
    }

    @Test
    fun statPercentBonusCities() {
        val civ = game.addCiv("[+200]% [Science] [in all cities]")
        val tile = game.getTile(Vector2(0f, 0f))
        val city = game.addCity(civ, tile, true)
        val building = game.createBuilding("[+10 Science]")
        city.cityConstructions.addBuilding(building.name)
        city.cityStats.update()

        Assert.assertTrue(city.cityStats.finalStatList["Buildings"]!!.science == 30f)
    }

    @Test
    fun statPercentFromObject() {
        game.makeHexagonalMap(1)
        val emptyBuilding = game.createBuilding()
        val civInfo = game.addCiv(
                "[+3 Faith] from every [Farm]",
                "[+200]% [Faith] from every [${emptyBuilding.name}]",
                "[+200]% [Faith] from every [Farm]",
            )
        val tile = game.setTileFeatures(Vector2(0f,0f), Constants.desert)
        val city = game.addCity(civInfo, tile, true)
        val faithBuilding = game.createBuilding()
        faithBuilding.faith = 3f
        city.cityConstructions.addBuilding(faithBuilding.name)

        val tile2 = game.setTileFeatures(Vector2(0f,1f), Constants.grassland)
        tile2.improvement = "Farm"
        Assert.assertTrue(tile2.getTileStats(city, civInfo).faith == 9f)

        city.cityConstructions.addBuilding(emptyBuilding.name)
        city.cityStats.update()

        Assert.assertTrue(city.cityStats.finalStatList["Buildings"]!!.faith == 9f)
    }

    @Test
    fun allStatsPercentFromObject() {
        game.makeHexagonalMap(1)
        val emptyBuilding = game.createBuilding()
        val civInfo = game.addCiv(
                "[+3 Faith] from every [Farm]",
                "[+200]% Yield from every [${emptyBuilding.name}]",
                "[+200]% Yield from every [Farm]",
            )
        val tile = game.setTileFeatures(Vector2(0f,0f), Constants.desert)
        val city = game.addCity(civInfo, tile, true)
        val faithBuilding = game.createBuilding()
        faithBuilding.faith = 3f
        city.cityConstructions.addBuilding(faithBuilding.name)

        val tile2 = game.setTileFeatures(Vector2(0f,1f), Constants.grassland)
        tile2.improvement = "Farm"
        Assert.assertTrue(tile2.getTileStats(city, civInfo).faith == 9f)

        city.cityConstructions.addBuilding(emptyBuilding.name)
        city.cityStats.update()

        Assert.assertTrue(city.cityStats.finalStatList["Buildings"]!!.faith == 9f)
    }


    // endregion


    @Test
    fun statsSpendingGreatPeople() {
        val civInfo = game.addCiv()
        val tile = game.setTileFeatures(Vector2(0f,0f), Constants.desert)
        val cityInfo = game.addCity(civInfo, tile, true)
        val unit = game.addUnit("Great Engineer", civInfo, tile)
        val building = game.createBuilding("[+250 Gold] whenever a Great Person is expended")
        cityInfo.cityConstructions.addBuilding(building.name)

        civInfo.addGold(-civInfo.gold) // reset gold just to be sure

        unit.consume()
        Assert.assertTrue(civInfo.gold == 250)
    }

}
