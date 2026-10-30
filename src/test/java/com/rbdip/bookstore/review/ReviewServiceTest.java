package com.rbdip.bookstore.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.rbdip.bookstore.purchase.PurchaseVerifier;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class ReviewServiceTest {

    private final ReviewRepository reviewRepository = mock(ReviewRepository.class);
    private final PurchaseVerifier purchaseVerifier = mock(PurchaseVerifier.class);
    private final ReviewService reviewService = new ReviewService(reviewRepository, purchaseVerifier);

    @Test
    void marksReviewOfBuyerAsVerified() {
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));
        when(purchaseVerifier.hasPurchased("Ivan Petrov", 7L)).thenReturn(true);

        ReviewService.AddedReview added = reviewService.addReview(7L, "Ivan Petrov", 5, "great");

        assertThat(added.verifiedPurchase()).isTrue();
    }

    @Test
    void savesAnonymousUnverifiedReviewWithoutAuthor() {
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));

        ReviewService.AddedReview added = reviewService.addReview(7L, null, 3, null);

        ArgumentCaptor<Review> saved = ArgumentCaptor.forClass(Review.class);
        Mockito.verify(reviewRepository).save(saved.capture());
        assertThat(saved.getValue().getAuthorName()).isEqualTo(ReviewService.ANONYMOUS_AUTHOR);
        assertThat(saved.getValue().getRating()).isEqualTo(3);
        assertThat(added.verifiedPurchase()).isFalse();
    }
}
