package com.helium.rentities.entities;

/** SSBO layout; STRIDE must match entity_vert.glsl and entity_cull.comp. */
public final class EntityInstance {
    public static final int STRIDE = 336;

    public static final int OFFSET_POSITION_X = 0;
    public static final int OFFSET_POSITION_Y = 4;
    public static final int OFFSET_POSITION_Z = 8;
    public static final int OFFSET_ROTATION_Y = 12;
    public static final int OFFSET_LIMB_SWING = 16;
    public static final int OFFSET_LIMB_SWING_AMT = 20;
    public static final int OFFSET_HEAD_YAW = 24;
    public static final int OFFSET_HEAD_PITCH = 28;
    public static final int OFFSET_ATTACK_PROGRESS = 32;
    public static final int OFFSET_BOW_PULL = 36;
    public static final int OFFSET_HURT_TIME = 40;
    public static final int OFFSET_DEATH_TIME = 44;
    public static final int OFFSET_SNEAK_PROGRESS = 48;
    public static final int OFFSET_SWIM_PROGRESS = 52;
    public static final int OFFSET_FLAGS = 56;
    public static final int OFFSET_RIPTIDE = 60;
    public static final int OFFSET_SIT_PROGRESS = 64;
    public static final int OFFSET_EAT_PROGRESS = 68;
    public static final int OFFSET_SWELL_AMOUNT = 72;
    public static final int OFFSET_EXPLODE_PROGRESS = 76;
    public static final int OFFSET_ROLL_PROGRESS = 80;
    public static final int OFFSET_ENTITY_TYPE = 84;
    public static final int OFFSET_ANIM_CATEGORY = 88;
    public static final int OFFSET_TEXTURE_LAYER = 92;
    public static final int OFFSET_GROUP_INDEX = 92;
    public static final int OFFSET_HELD_MAIN = 96;
    public static final int OFFSET_HELD_OFFHAND = 100;
    public static final int OFFSET_ARMOR_HEAD = 104;
    public static final int OFFSET_ARMOR_CHEST = 108;
    public static final int OFFSET_ARMOR_LEGS = 112;
    public static final int OFFSET_ARMOR_FEET = 116;
    public static final int OFFSET_MOUNT_ID = 120;
    public static final int OFFSET_SEAT_OFFSET_X = 124;
    public static final int OFFSET_SEAT_OFFSET_Y = 128;
    public static final int OFFSET_SEAT_OFFSET_Z = 132;
    public static final int OFFSET_TEX_SCALE_X = 136;
    public static final int OFFSET_TEX_SCALE_Y = 140;
    public static final int OFFSET_HEAD_PIVOT = 144;
    public static final int OFFSET_HEAD_PIVOT_X = 144;
    public static final int OFFSET_HEAD_PIVOT_Y = 148;
    public static final int OFFSET_HEAD_PIVOT_Z = 152;

    public static final int OFFSET_EXACT_POSE_0 = 160;
    public static final int OFFSET_EXACT_POSE_1 = 176;
    public static final int OFFSET_EXACT_POSE_2 = 192;
    public static final int OFFSET_EXACT_POSE_3 = 208;
    public static final int OFFSET_EXACT_POSE_4 = 224;
    public static final int OFFSET_EXACT_POSE_5 = 240;
    public static final int OFFSET_EXACT_POSE_6 = 256;
    public static final int OFFSET_EXACT_POSE_7 = 272;
    public static final int OFFSET_EXACT_POSE_8 = 288;
    public static final int OFFSET_EXACT_POSE_9 = 304;

    public static final int OFFSET_ARMOR_STAND_HEAD_POSE = OFFSET_EXACT_POSE_0;
    public static final int OFFSET_ARMOR_STAND_BODY_POSE = OFFSET_EXACT_POSE_1;
    public static final int OFFSET_ARMOR_STAND_LEFT_ARM_POSE = OFFSET_EXACT_POSE_2;
    public static final int OFFSET_ARMOR_STAND_RIGHT_ARM_POSE = OFFSET_EXACT_POSE_3;
    public static final int OFFSET_ARMOR_STAND_LEFT_LEG_POSE = OFFSET_EXACT_POSE_4;
    public static final int OFFSET_ARMOR_STAND_RIGHT_LEG_POSE = OFFSET_EXACT_POSE_5;

    public static final int OFFSET_PACKED_LIGHT = 320;
    public static final int OFFSET_SLIME_SCALE_XZ = 324;
    public static final int OFFSET_SLIME_SCALE_Y = 328;
    public static final int OFFSET_MATERIAL_FLAGS = 332;

    public static final int FLAG_IS_BLOCKING = 1;
    public static final int FLAG_IS_GLIDING = 2;
    public static final int FLAG_HAS_GLINT = 4;
    public static final int FLAG_IS_IN_WATER = 8;
    public static final int FLAG_IS_ALEX = 16;
    public static final int FLAG_IS_PLAYER = 32;
    public static final int FLAG_IS_INVISIBLE = 64;
    public static final int FLAG_ON_GROUND = 128;
    public static final int FLAG_ZOMBIE_ARMS = 256;
    public static final int FLAG_SLIME = 1024;
    public static final int FLAG_EXACT_MODEL_POSE = 512;
    public static final int FLAG_ARMOR_STAND = FLAG_EXACT_MODEL_POSE;
    /** Use the two legacy slime-scale slots as a generic renderer scale for non-slimes. */
    public static final int FLAG_MODEL_SCALE = 2048;

    public static final int NO_MOUNT = -1;
    public static final int NO_ITEM = -1;
    public static final int NO_ARMOR = -1;
    public static final int MAX_INSTANCES = 16_384;
    public static final long SSBO_SIZE = (long) MAX_INSTANCES * STRIDE;

    private EntityInstance() {}
}
