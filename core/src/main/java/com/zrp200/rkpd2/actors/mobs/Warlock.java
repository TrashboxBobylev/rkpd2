/*
 * Pixel Dungeon
 * Copyright (C) 2012-2015 Oleg Dolya
 *
 * Shattered Pixel Dungeon
 * Copyright (C) 2014-2024 Evan Debenham
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */

package com.zrp200.rkpd2.actors.mobs;

import com.watabou.noosa.audio.Sample;
import com.watabou.utils.Callback;
import com.watabou.utils.Random;
import com.zrp200.rkpd2.Assets;
import com.zrp200.rkpd2.Badges;
import com.zrp200.rkpd2.Dungeon;
import com.zrp200.rkpd2.actors.Char;
import com.zrp200.rkpd2.actors.buffs.*;
import com.zrp200.rkpd2.actors.hero.Talent;
import com.zrp200.rkpd2.effects.Speck;
import com.zrp200.rkpd2.effects.SpellSprite;
import com.zrp200.rkpd2.actors.buffs.Invisibility;
import com.zrp200.rkpd2.items.Generator;
import com.zrp200.rkpd2.items.Item;
import com.zrp200.rkpd2.items.potions.PotionOfHealing;
import com.zrp200.rkpd2.mechanics.Ballistica;
import com.zrp200.rkpd2.messages.Messages;
import com.zrp200.rkpd2.sprites.CharSprite;
import com.zrp200.rkpd2.sprites.WarlockSprite;
import com.zrp200.rkpd2.utils.GLog;

public class Warlock extends Mob implements Callback {
	
	private static final float TIME_TO_ZAP	= 1f;
	
	{
		spriteClass = WarlockSprite.class;
		
		HP = HT = 70;
		defenseSkill = 18;
		
		EXP = 11;
		maxLvl = 21;
		
		loot = Generator.Category.POTION;
		lootChance = 0.5f;

		properties.add(Property.UNDEAD);
	}
	
	@Override
	public int damageRoll() {
		return Random.NormalIntRange( 12, 18 );
	}
	
	@Override
	public int attackSkill( Char target ) {
		return 25;
	}
	
	@Override
	public int drRoll() {
		return super.drRoll() + Random.NormalIntRange(0, 8);
	}
	
	@Override
	public boolean canAttack(Char enemy) {
		if (buff(ChampionEnemy.Paladin.class) != null){
			return false;
		}
		return super.canAttack(enemy)
				|| new Ballistica( pos, enemy.pos, Ballistica.MAGIC_BOLT).collisionPos == enemy.pos;
	}
	
	protected boolean doAttack( Char enemy ) {

		if (Dungeon.level.adjacent( pos, enemy.pos )
				|| new Ballistica( pos, enemy.pos, Ballistica.MAGIC_BOLT).collisionPos != enemy.pos) {
			
			return super.doAttack( enemy );
			
		} else {
			
			if (sprite != null && (sprite.visible || enemy.sprite.visible)) {
				sprite.zap( enemy.pos );
				return false;
			} else {
				zap();
				return true;
			}
		}
	}
	
	//used so resistances can differentiate between melee and magical attacks
	public static class DarkBolt{}
	
	protected void zap() {
		spend( TIME_TO_ZAP );

		Invisibility.dispel(this);
		Char enemy = this.enemy;
		if (hit( this, enemy, true )) {
			//TODO would be nice for this to work on ghost/statues too
			if (enemy == Dungeon.hero && enemy.buff(WarriorParry.BlockTrock.class) == null && Random.Int( 2 ) == 0) {
				Buff.prolong( enemy, Degrade.class, Degrade.DURATION );
				Sample.INSTANCE.play( Assets.Sounds.DEBUFF );
			}
			
			int dmg = Random.NormalIntRange( 12, 18 );
			if (buff(Shrink.class) != null|| enemy.buff(TimedShrink.class) != null) dmg *= 0.6f;
			ChampionEnemy.AntiMagic.effect(enemy, this);
			dmg = Math.round(dmg * AscensionChallenge.statModifier(this));
			if (enemy.buff(WarriorParry.BlockTrock.class) != null){
				enemy.sprite.emitter().burst( Speck.factory( Speck.FORGE ), 15 );
				SpellSprite.show(enemy, SpellSprite.MAP, 2f, 2f, 2f);
				Buff.affect(enemy, Barrier.class).setShield(Math.round(dmg*1.25f));
				Buff.detach(enemy, WarriorParry.BlockTrock.class);
			} else {
				enemy.damage(dmg, new DarkBolt());

				if (enemy == Dungeon.hero && !enemy.isAlive()) {
					Badges.validateDeathFromEnemyMagic();
					Dungeon.fail(this);
					GLog.n(Messages.get(this, "bolt_kill"));
				}
			}
		} else {
			enemy.sprite.showStatus( CharSprite.NEUTRAL,  enemy.defenseVerb() );
		}
	}
	
	public void onZapComplete() {
		zap();
		next();
	}
	
	@Override
	public void call() {
		next();
	}

	@Override
	public Item createLoot(){

		// 1/6 chance for healing, scaling to 0 over 8 drops
		if (Random.Int(3) == 0 && Random.Int(8) > Dungeon.LimitedDrops.WARLOCK_HP.count ){
			Dungeon.LimitedDrops.WARLOCK_HP.count++;
			return new PotionOfHealing();
		} else {
			Item i;
			do {
				i = Generator.randomUsingDefaults(Generator.Category.POTION);
			} while (i instanceof PotionOfHealing);
			return i;
		}

	}
}
