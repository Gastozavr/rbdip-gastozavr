package com.rbdip.bookstore.review;

import com.rbdip.bookstore.purchase.PurchaseVerifier;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Отзывы на товары. Факт покупки проверяется только через контракт
 * {@link PurchaseVerifier} - без зависимости от внутренних классов order.
 */
@Service
public class ReviewService {

    static final String ANONYMOUS_AUTHOR = "anonymous";

    private final ReviewRepository reviewRepository;
    private final PurchaseVerifier purchaseVerifier;

    public ReviewService(ReviewRepository reviewRepository, PurchaseVerifier purchaseVerifier) {
        this.reviewRepository = reviewRepository;
        this.purchaseVerifier = purchaseVerifier;
    }

    @Transactional
    public AddedReview addReview(Long productId, String authorName, Integer rating, String comment) {
        boolean verifiedPurchase = purchaseVerifier.hasPurchased(authorName, productId);
        String author = authorName == null ? ANONYMOUS_AUTHOR : authorName;
        Review review = reviewRepository.save(new Review(productId, author, rating, comment));
        return new AddedReview(review.getId(), verifiedPurchase);
    }

    @Transactional(readOnly = true)
    public List<Review> listReviews(Long productId) {
        return reviewRepository.findByProductId(productId);
    }

    public record AddedReview(Long id, boolean verifiedPurchase) {
    }
}
