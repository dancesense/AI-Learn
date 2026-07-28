import { integer, real, sqliteTable, text, uniqueIndex } from "drizzle-orm/sqlite-core";

export const users = sqliteTable("users", {
  id: integer("id").primaryKey({ autoIncrement: true }),
  displayName: text("display_name").notNull(),
  role: text("role").notNull(),
  verified: integer("verified", { mode: "boolean" }).notNull().default(false),
  avatar: text("avatar"),
  collectionsCount: integer("collections_count").notNull().default(0),
  ordersCount: integer("orders_count").notNull().default(0),
  communitiesCount: integer("communities_count").notNull().default(0),
  messagesCount: integer("messages_count").notNull().default(0),
});

export const userAccounts = sqliteTable(
  "user_accounts",
  {
    id: integer("id").primaryKey({ autoIncrement: true }),
    userId: integer("user_id").notNull().references(() => users.id),
    email: text("email").notNull(),
    passwordHash: text("password_hash").notNull(),
    createdAt: text("created_at").notNull(),
  },
  (table) => [
    uniqueIndex("user_accounts_user_id_uq").on(table.userId),
    uniqueIndex("user_accounts_email_uq").on(table.email),
  ],
);

export const authSessions = sqliteTable("auth_sessions", {
  token: text("token").primaryKey(),
  userId: integer("user_id").notNull().references(() => users.id),
  expiresAt: text("expires_at").notNull(),
  createdAt: text("created_at").notNull(),
});

export const contentItems = sqliteTable("content_items", {
  id: integer("id").primaryKey({ autoIncrement: true }),
  title: text("title").notNull(),
  views: text("views").notNull(),
  cover: text("cover").notNull(),
  author: text("author").notNull(),
  authorAvatar: text("author_avatar"),
  likes: integer("likes").notNull().default(0),
  comments: integer("comments").notNull().default(0),
  category: text("category").notNull(),
});

export const tutors = sqliteTable("tutors", {
  id: integer("id").primaryKey({ autoIncrement: true }),
  name: text("name").notNull(),
  school: text("school").notNull(),
  tags: text("tags").notNull(),
  subjects: text("subjects").notNull(),
  grades: text("grades").notNull(),
  price: real("price").notNull(),
  avatar: text("avatar"),
  description: text("description"),
  online: integer("online", { mode: "boolean" }).notNull().default(true),
});

export const communities = sqliteTable("communities", {
  id: integer("id").primaryKey({ autoIncrement: true }),
  name: text("name").notNull(),
  description: text("description").notNull(),
  members: integer("members").notNull().default(0),
  cover: text("cover").notNull(),
});

export const reservations = sqliteTable("reservations", {
  id: integer("id").primaryKey({ autoIncrement: true }),
  orderNo: text("order_no").notNull().unique(),
  userId: integer("user_id").notNull().references(() => users.id),
  tutorId: integer("tutor_id").notNull().references(() => tutors.id),
  subject: text("subject").notNull(),
  scheduledAt: text("scheduled_at").notNull(),
  status: text("status").notNull(),
  createdAt: text("created_at").notNull(),
});

export const memberships = sqliteTable(
  "memberships",
  {
    id: integer("id").primaryKey({ autoIncrement: true }),
    userId: integer("user_id").notNull().references(() => users.id),
    communityId: integer("community_id").notNull().references(() => communities.id),
    joinedAt: text("joined_at").notNull(),
  },
  (table) => [uniqueIndex("memberships_user_community_uq").on(table.userId, table.communityId)],
);

export const contentLikes = sqliteTable(
  "content_likes",
  {
    id: integer("id").primaryKey({ autoIncrement: true }),
    userId: integer("user_id").notNull().references(() => users.id),
    contentId: integer("content_id").notNull().references(() => contentItems.id),
    createdAt: text("created_at").notNull(),
  },
  (table) => [uniqueIndex("content_likes_user_content_uq").on(table.userId, table.contentId)],
);

export const creatorFollows = sqliteTable(
  "creator_follows",
  {
    id: integer("id").primaryKey({ autoIncrement: true }),
    userId: integer("user_id").notNull().references(() => users.id),
    creatorName: text("creator_name").notNull(),
    followedAt: text("followed_at").notNull(),
  },
  (table) => [uniqueIndex("creator_follows_user_creator_uq").on(table.userId, table.creatorName)],
);
