package com.thomas.espdoorbell.doorbell.config

import com.thomas.espdoorbell.doorbell.model.types.*
import io.r2dbc.pool.ConnectionPool
import io.r2dbc.pool.ConnectionPoolConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionFactory
import io.r2dbc.postgresql.codec.EnumCodec
import io.r2dbc.spi.ConnectionFactory
import org.springframework.boot.autoconfigure.r2dbc.R2dbcProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.auditing.DateTimeProvider
import org.springframework.data.convert.WritingConverter
import org.springframework.data.r2dbc.config.AbstractR2dbcConfiguration
import org.springframework.data.r2dbc.config.EnableR2dbcAuditing
import org.springframework.data.r2dbc.convert.EnumWriteSupport
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.TemporalAccessor
import java.util.*

@Configuration
@EnableR2dbcRepositories
@EnableConfigurationProperties(R2dbcProperties::class)
@EnableR2dbcAuditing(
//    auditorAwareRef = "reactiveAuditorAware",
    dateTimeProviderRef = "auditingDateTimeProvider",
    modifyOnCreate = true
)
class R2DBCConfig(
    private val r2dbcProperties: R2dbcProperties,
): AbstractR2dbcConfiguration() {
    companion object {
        val VIETNAM_ZONE: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")
    }

//    @Bean
//    fun reactiveAuditorAware(): ReactiveAuditorAware<UUID> = ReactiveAuditorAware {
//        TODO: Extract the authenticated principal (e.g. from ReactiveSecurityContextHolder)
//        Mono.empty()
//    }

    @Bean
    override fun connectionFactory(): ConnectionFactory {
        val details = r2dbcProperties.url.substringAfter("//")
        val (host, port) = details.substringBefore("/").split(":")
        val database = details.substringAfter("/")

        val pgConfig = PostgresqlConnectionConfiguration.builder()
            .host(host)
            .port(port.toInt())
            .database(database)
            .username(r2dbcProperties.username)
            .password(r2dbcProperties.password)
            .codecRegistrar(
                EnumCodec.builder()
                    .withEnum("stream_status_enum", StreamStatus::class.java)
                    .withEnum("event_type_enum", EventType::class.java)
                    .withEnum("response_type_enum", ResponseType::class.java)
                    .withEnum("auth_provider_enum", AuthProvider::class.java)
                    .withEnum("granted_status_enum", DeviceAccess::class.java)
                    .withEnum("notification_type_enum", NotificationMethod::class.java)
                    .withEnum("user_role_enum", UserRole::class.java)
                    .build()
            )
            .build()

        val pgFactory =  PostgresqlConnectionFactory(pgConfig)

        val poolConfig = ConnectionPoolConfiguration.builder(pgFactory)
            .initialSize(r2dbcProperties.pool.initialSize)
            .maxSize(r2dbcProperties.pool.maxSize)
            .maxIdleTime(r2dbcProperties.pool.maxIdleTime)
            .validationQuery(r2dbcProperties.pool.validationQuery)
            .build()

        return ConnectionPool(poolConfig)
    }

    @Bean
    fun auditingDateTimeProvider(): DateTimeProvider = DateTimeProvider {
        Optional.of(OffsetDateTime.now(VIETNAM_ZONE) as TemporalAccessor)
    }

    @Bean
    override fun getCustomConverters(): List<Any?> = listOf(
        StreamStatusWriteConverter,
        EventTypeWriteConverter,
        ResponseTypeWriteConverter,
        AuthProviderWriteConverter,
        DeviceAccessWriteConverter,
        NotificationMethodWriteConverter,
        UserRoleWriteConverter
    )

    // === Enum Converters ===
    @WritingConverter
    object StreamStatusWriteConverter : EnumWriteSupport<StreamStatus>()

    @WritingConverter
    object EventTypeWriteConverter : EnumWriteSupport<EventType>()

    @WritingConverter
    object ResponseTypeWriteConverter : EnumWriteSupport<ResponseType>()

    @WritingConverter
    object AuthProviderWriteConverter: EnumWriteSupport<AuthProvider>()

    @WritingConverter
    object DeviceAccessWriteConverter: EnumWriteSupport<DeviceAccess>()

    @WritingConverter
    object NotificationMethodWriteConverter: EnumWriteSupport<NotificationMethod>()

    @WritingConverter
    object UserRoleWriteConverter: EnumWriteSupport<UserRole>()
}
