CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    display_name VARCHAR(80) NOT NULL,
    role VARCHAR(30) NOT NULL,
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    avatar VARCHAR(255),
    collections_count INT NOT NULL DEFAULT 0,
    orders_count INT NOT NULL DEFAULT 0,
    communities_count INT NOT NULL DEFAULT 0,
    messages_count INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS user_accounts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    email VARCHAR(160) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_account_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS auth_sessions (
    token VARCHAR(64) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_session_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS content_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(160) NOT NULL,
    views VARCHAR(30) NOT NULL,
    cover VARCHAR(255) NOT NULL,
    author VARCHAR(80) NOT NULL,
    author_avatar VARCHAR(255),
    likes INT NOT NULL DEFAULT 0,
    comments INT NOT NULL DEFAULT 0,
    category VARCHAR(50) NOT NULL
);

CREATE TABLE IF NOT EXISTS tutors (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(80) NOT NULL,
    school VARCHAR(120) NOT NULL,
    tags VARCHAR(255) NOT NULL,
    subjects VARCHAR(255) NOT NULL,
    grades VARCHAR(120) NOT NULL,
    price DECIMAL(10, 2) NOT NULL,
    avatar VARCHAR(255),
    description VARCHAR(255),
    online BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS communities (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(255) NOT NULL,
    members INT NOT NULL DEFAULT 0,
    cover VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS reservations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(40) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    tutor_id BIGINT NOT NULL,
    subject VARCHAR(80) NOT NULL,
    scheduled_at TIMESTAMP NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_reservation_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_reservation_tutor FOREIGN KEY (tutor_id) REFERENCES tutors(id)
);

CREATE TABLE IF NOT EXISTS memberships (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    community_id BIGINT NOT NULL,
    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_membership UNIQUE (user_id, community_id),
    CONSTRAINT fk_membership_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_membership_community FOREIGN KEY (community_id) REFERENCES communities(id)
);

CREATE TABLE IF NOT EXISTS content_likes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    content_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_content_like UNIQUE (user_id, content_id),
    CONSTRAINT fk_like_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_like_content FOREIGN KEY (content_id) REFERENCES content_items(id)
);

CREATE TABLE IF NOT EXISTS creator_follows (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    creator_name VARCHAR(80) NOT NULL,
    followed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_creator_follow UNIQUE (user_id, creator_name),
    CONSTRAINT fk_follow_user FOREIGN KEY (user_id) REFERENCES users(id)
);
