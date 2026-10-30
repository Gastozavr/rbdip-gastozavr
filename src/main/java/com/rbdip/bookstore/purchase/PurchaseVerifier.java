package com.rbdip.bookstore.purchase;

/**
 * Явный контракт "покупал ли клиент товар" между модулями. Модуль review
 * зависит только от него, а реализация живёт в модуле order
 * (Strangler Fig: старая прямая связь review → order-репозитории заменена
 * фасадом, за которым order можно менять или вынести в отдельный сервис).
 */
public interface PurchaseVerifier {

    boolean hasPurchased(String customerFullName, Long productId);
}
